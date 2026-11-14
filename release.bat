@echo off
rem ===========================================================================
rem  Minecraft Music Player - local release build
rem
rem  Builds the currently checked out branch and reports the mod jar that Gradle
rem  produced. The artifacts stay exactly where IDEA and Gradle put them
rem  (build\libs), so no extra copy folder is created by default.
rem
rem  The script only builds and reports: it never tags, commits or publishes
rem  anything. Every version specific value (minecraft_version, mod_version,
rem  Java release target) is read from the checked out branch, so the same
rem  script works on all version branches.
rem
rem  Usage:
rem    release.bat
rem    release.bat -Version 2.3.0
rem    release.bat -OutputDir dist
rem    release.bat -NoClean -SkipTests
rem    release.bat -h
rem ===========================================================================

setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "VERSION="
set "OUTDIR=build\libs"
set "NOCLEAN=0"
set "SKIPTESTS=0"
set "EXITCODE=0"
set "LOGFILE="

rem ---------------------------------------------------------------------------
rem  Arguments
rem ---------------------------------------------------------------------------
:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="-h"         goto usage
if /i "%~1"=="/?"         goto usage
if /i "%~1"=="--help"     goto usage
if /i "%~1"=="-NoClean"   ( set "NOCLEAN=1"   & shift & goto parse_args )
if /i "%~1"=="-SkipTests" ( set "SKIPTESTS=1" & shift & goto parse_args )
if /i "%~1"=="-Version" (
    if "%~2"=="" goto missing_value
    set "VERSION=%~2"
    shift
    shift
    goto parse_args
)
if /i "%~1"=="-OutputDir" (
    if "%~2"=="" goto missing_value
    set "OUTDIR=%~2"
    shift
    shift
    goto parse_args
)
if /i "%~1"=="-LogFile" (
    if "%~2"=="" goto missing_value
    set "LOGFILE=%~2"
    shift
    shift
    goto parse_args
)
echo error: unknown argument "%~1"
echo.
goto usage

:args_done

rem ---------------------------------------------------------------------------
rem  Branch metadata
rem ---------------------------------------------------------------------------
set "MCVER="
set "DEFVER="
for /f "usebackq tokens=1,* delims== " %%a in (`findstr /b /c:"minecraft_version=" gradle.properties`) do set "MCVER=%%b"
for /f "usebackq tokens=1,* delims== " %%a in (`findstr /b /c:"mod_version=" gradle.properties`) do set "DEFVER=%%b"

if not defined MCVER (
    echo error: gradle.properties is missing minecraft_version
    set "EXITCODE=1"
    goto end
)
if not defined DEFVER (
    echo error: gradle.properties is missing mod_version
    set "EXITCODE=1"
    goto end
)
if not exist "gradlew.bat" (
    echo error: gradlew.bat not found in "%CD%"
    set "EXITCODE=1"
    goto end
)

rem ---------------------------------------------------------------------------
rem  Version override
rem ---------------------------------------------------------------------------
if defined VERSION (
    if /i "!VERSION:~0,1!"=="v" set "VERSION=!VERSION:~1!"
    call :validate_version "!VERSION!"
    if defined BADVER (
        echo error: invalid version "!VERSION!": expected something like 2.3.0
        set "EXITCODE=1"
        goto end
    )
    set "EFFVER=!VERSION!"
) else (
    set "EFFVER=!DEFVER!"
)

rem ---------------------------------------------------------------------------
rem  Java sanity check (Gradle needs at least the release target of the branch)
rem ---------------------------------------------------------------------------
set "REQREL="
for /f "usebackq tokens=2 delims== " %%a in (`findstr /c:"options.release" build.gradle`) do set "REQREL=%%a"

set "JAVAEXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVAEXE=%JAVA_HOME%\bin\java.exe"

set "JAVALOG=%TEMP%\musicplayer-javaver.txt"
"%JAVAEXE%" -version > "%JAVALOG%" 2>&1
if errorlevel 1 (
    echo error: "%JAVAEXE%" could not be started; set JAVA_HOME to a working JDK
    del /q "%JAVALOG%" >nul 2>&1
    set "EXITCODE=1"
    goto end
)

set "JAVESTR="
for /f "usebackq tokens=3 delims= " %%a in (`findstr /i "version" "%JAVALOG%"`) do if not defined JAVESTR set "JAVESTR=%%a"
del /q "%JAVALOG%" >nul 2>&1

set "JAVAMAJOR="
set "JAVAMINOR="
if defined JAVESTR (
    set JAVESTR=!JAVESTR:"=!
    for /f "tokens=1,2 delims=." %%a in ("!JAVESTR!") do (
        set "JAVAMAJOR=%%a"
        set "JAVAMINOR=%%b"
    )
    if "!JAVAMAJOR!"=="1" if defined JAVAMINOR set "JAVAMAJOR=!JAVAMINOR!"
)

if defined REQREL if defined JAVAMAJOR (
    if !JAVAMAJOR! LSS !REQREL! (
        echo error: Java !JAVAMAJOR! is too old for this branch, which needs Java !REQREL!.
        echo        Point JAVA_HOME at a newer JDK and try again.
        set "EXITCODE=1"
        goto end
    )
)

rem ---------------------------------------------------------------------------
rem  Output folder and log location
rem ---------------------------------------------------------------------------
rem The default output folder is build\libs, i.e. exactly the folder IDEA and
rem Gradle already use. A custom -OutputDir is treated as an extra copy target.
for %%d in ("%OUTDIR%") do set "OUTABS=%%~fd"
for %%d in ("build\libs") do set "LIBDIR=%%~fd"

set "INPLACE=0"
if /i "!OUTABS!"=="!LIBDIR!" set "INPLACE=1"

if "!INPLACE!"=="0" (
    if not exist "!OUTABS!" mkdir "!OUTABS!" >nul 2>&1
    if not exist "!OUTABS!" (
        echo error: cannot create output folder "%OUTDIR%"
        set "EXITCODE=1"
        goto end
    )
)

rem The build log is written to while Gradle runs, so it must live outside
rem build\: the clean task would otherwise fail to delete the open file.
if not defined LOGFILE set "LOGFILE=.gradle\musicplayer-release.log"
for %%d in ("!LOGFILE!") do set "LOGABS=%%~fd"
for %%d in ("build") do set "BUILDDIR=%%~fd"
set "BUILDPFX=!BUILDDIR!\"

call set "LOGCHECK=%%LOGABS:!BUILDPFX!=~%%"
if not "!LOGCHECK!"=="!LOGABS!" (
    echo error: the log file must not be inside build\, because the clean task
    echo        cannot delete it while it is being written: "!LOGABS!"
    set "EXITCODE=1"
    goto end
)
for %%d in ("!LOGABS!\..") do set "LOGDIR=%%~fd"
if not exist "!LOGDIR!" mkdir "!LOGDIR!" >nul 2>&1

rem ---------------------------------------------------------------------------
rem  Gradle arguments
rem ---------------------------------------------------------------------------
set "GRADLEARGS="
if "%NOCLEAN%"=="0" set "GRADLEARGS=clean "
set "GRADLEARGS=%GRADLEARGS%build --console=plain"
if "%SKIPTESTS%"=="1" set "GRADLEARGS=%GRADLEARGS% -x test"
if defined VERSION set "GRADLEARGS=%GRADLEARGS% -Pmod_version=%VERSION%"

set "BRANCH=unknown"
for /f "usebackq delims=" %%b in (`git rev-parse --abbrev-ref HEAD 2^>nul`) do set "BRANCH=%%b"
set "DIRTY=0"
for /f "usebackq delims=" %%s in (`git status --porcelain 2^>nul`) do set "DIRTY=1"

echo ==^> Building !BRANCH! ^(Minecraft !MCVER!, version !EFFVER!^)
echo     gradle  : gradlew.bat !GRADLEARGS!
echo     java    : !JAVAEXE! ^(major !JAVAMAJOR!, target release !REQREL!^)
echo     output  : !LIBDIR!
echo     log     : !LOGABS!
if "!DIRTY!"=="1" echo     warning : the working tree has uncommitted changes
echo.

rem ---------------------------------------------------------------------------
rem  Build
rem ---------------------------------------------------------------------------
call gradlew.bat !GRADLEARGS! > "!LOGABS!" 2>&1
set "BUILDEXIT=!ERRORLEVEL!"

if not "!BUILDEXIT!"=="0" (
    echo Build failed with exit code !BUILDEXIT!.
    echo.
    findstr /i /c:"error:" /c:"FAILURE" /c:"What went wrong" /c:"Could not" "!LOGABS!" 2>nul
    echo.
    echo Full log: !LOGABS!
    set "EXITCODE=!BUILDEXIT!"
    goto end
)

findstr /c:"BUILD SUCCESSFUL" "!LOGABS!" >nul 2>&1
if errorlevel 1 (
    echo error: Gradle exited with code 0 but reported no success line.
    echo        Full log: !LOGABS!
    set "EXITCODE=1"
    goto end
)
echo Build succeeded.

rem ---------------------------------------------------------------------------
rem  Locate the mod jar in build\libs
rem ---------------------------------------------------------------------------
if not exist "!LIBDIR!" (
    echo error: build output folder not found: "!LIBDIR!"
    set "EXITCODE=1"
    goto end
)

set "MAINJAR="
set "MAINCOUNT=0"
set "SRCJAR="
for %%f in ("!LIBDIR!\*.jar") do call :classify "%%~ff"

if not "!MAINCOUNT!"=="1" (
    echo error: expected exactly 1 mod jar in "!LIBDIR!", found !MAINCOUNT!
    echo        the folder contains:
    for %%f in ("!LIBDIR!\*.jar") do if exist "%%~ff" echo            %%~nxf
    if "!MAINCOUNT!"=="0" echo        run again without -NoClean to force a rebuild
    if !MAINCOUNT! GTR 1 echo        stale jars from older versions are present; run again without -NoClean
    set "EXITCODE=1"
    goto end
)

for %%x in ("!MAINJAR!") do set "MAINNAME=%%~nxx"
set "NEEDLE=fabricmc!MCVER!"
call set "NAMECHECK=%%MAINNAME:!NEEDLE!=~%%"
if "!NAMECHECK!"=="!MAINNAME!" (
    echo error: jar name "!MAINNAME!" does not contain the expected "fabricmc!MCVER!".
    echo        The jar may be stale; run again without -NoClean.
    set "EXITCODE=1"
    goto end
)

rem ---------------------------------------------------------------------------
rem  Optional extra copy
rem ---------------------------------------------------------------------------
if "!INPLACE!"=="0" (
    copy /y "!MAINJAR!" "!OUTABS!\" >nul
    if errorlevel 1 (
        echo error: could not copy "!MAINJAR!" to "!OUTABS!"
        set "EXITCODE=1"
        goto end
    )
    if defined SRCJAR copy /y "!SRCJAR!" "!OUTABS!\" >nul
    for %%x in ("!MAINJAR!") do set "MAINJAR=!OUTABS!\%%~nxx"
)

rem ---------------------------------------------------------------------------
rem  Summary
rem ---------------------------------------------------------------------------
echo.
echo ==^> Build finished
echo     branch     : !BRANCH!
echo     minecraft  : !MCVER!
echo     mod version: !EFFVER!
echo     output     : !OUTABS!
echo.
call :report "!MAINJAR!"
if defined SRCJAR call :report "!SRCJAR!"
echo.
echo     Local artifacts only; nothing was tagged, committed or published.
echo.
goto end

rem ---------------------------------------------------------------------------
rem  Subroutines
rem ---------------------------------------------------------------------------

rem Sets BADVER when the version string contains characters that are not
rem [0-9A-Za-z._-] or does not start with a letter or digit.
:validate_version
set "BADVER="
set "VSAN=%~1"
if not defined VSAN (
    set "BADVER=1"
    goto :eof
)
for %%c in (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) do if defined VSAN set "VSAN=!VSAN:%%c=!"
for %%c in (a b c d e f g h i j k l m n o p q r s t u v w x y z) do if defined VSAN set "VSAN=!VSAN:%%c=!"
for %%c in (0 1 2 3 4 5 6 7 8 9 . _ -) do if defined VSAN set "VSAN=!VSAN:%%c=!"
if defined VSAN (
    set "BADVER=1"
    goto :eof
)
rem The first character must be a letter or a digit.
set "VFIRST=%~1"
set "VFIRST=!VFIRST:~0,1!"
set "FIRSTOK="
for %%c in (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) do if /i "!VFIRST!"=="%%c" set "FIRSTOK=1"
for %%c in (0 1 2 3 4 5 6 7 8 9) do if "!VFIRST!"=="%%c" set "FIRSTOK=1"
if not defined FIRSTOK set "BADVER=1"
goto :eof

:classify
set "CFULL=%~1"
set "CNAME=%~nx1"
if not exist "!CFULL!" goto :eof
set "CSRC=!CNAME!"
set "CSRC=!CSRC:-sources.jar=~!"
if not "!CSRC!"=="!CNAME!" (
    if not defined SRCJAR set "SRCJAR=!CFULL!"
    goto :eof
)
set "CMOD=!CNAME!"
set "CMOD=!CMOD:-fabricmc=~!"
if "!CMOD!"=="!CNAME!" goto :eof
set /a MAINCOUNT+=1
set "MAINJAR=!CFULL!"
goto :eof

:report
set "RFILE=%~1"
set "RNAME=%~nx1"
if not exist "!RFILE!" goto :eof
set "RSIZE=0"
for %%x in ("!RFILE!") do set "RSIZE=%%~zx"
set "RHASH="
rem certutil prints a header, then the 64 character hash, then a summary line.
rem Validate by length because findstr regex is not reliable in batch.
for /f "usebackq skip=1 delims=" %%h in (`certutil -hashfile "!RFILE!" SHA256 2^>nul`) do (
    if not defined RHASH (
        set "CAND=%%h"
        set "CAND=!CAND: =!"
        if not "!CAND:~63!"=="" if "!CAND:~64!"=="" set "RHASH=!CAND!"
    )
)
if not defined RHASH set "RHASH=unavailable"
echo     !RNAME!
echo       size   : !RSIZE! bytes
echo       sha256 : !RHASH!
goto :eof

:missing_value
echo error: %~1 requires a value
echo.
goto usage

:usage
echo.
echo Usage: release.bat [-Version X.Y.Z] [-OutputDir DIR] [-NoClean] [-SkipTests] [-LogFile FILE]
echo.
echo   -Version VER    build with -Pmod_version=VER (not written to disk)
echo   -OutputDir DIR  extra copy target; jars always build into build\libs
echo   -NoClean        skip the clean task
echo   -SkipTests      skip the test task
echo   -LogFile FILE   Gradle log, defaults to .gradle\musicplayer-release.log
echo   -h              show this help
echo.
if not defined EXITCODE set "EXITCODE=1"
goto end

:end
endlocal & exit /b %EXITCODE%
