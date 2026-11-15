# 图标生成脚本

生成模组图标 `src/main/resources/assets/musicplayer/icon.png`（128×128）：唱片机侧面 10×10 红色封面 + 白色网易云 logo。

## 用法

```powershell
node tools/icon/render-icon.js
```

脚本会在 512×512 分辨率下绘制，再按 alpha 加权降采样到 128×128，覆盖上面的图标文件。可选参数为输出路径：

```powershell
node tools/icon/render-icon.js out.png
```

只依赖 Node.js 内置模块（`fs` / `path` / `zlib`），无需 `npm install`。

## 文件

| 文件 | 说明 |
| --- | --- |
| `render-icon.js` | 渲染脚本，只输出最终的 128×128 图标 |
| `data/jukebox-side.json` | 原版 `jukebox_side` 16×16 侧面贴图像素表（每像素 RRGGBB） |
| `data/netease-mask.gray.z` | 网易云 SDK logo 的 1024×1024 8 位灰度蒙版，deflate 压缩（解压后 1 MiB） |

## 设计要点

- 封面区域：贴图坐标 `u∈[3,13)`、`v∈[4,14)`，底色 `#c20c0c`。
- 槽深 1 像素的立体感：上沿投影（`top0=0.45`、`top1=0.72`、`top2=0.92`），左右侧壁阴影（`left=0.66`、`right=0.82`），下沿受光（`+42`，倒数第二行 `+10`）。
- 封面最上面两行保留 6 个原版材质像素：第一行左右各 2 像素，第二行左右各 1 像素。
- logo 高度 6 个贴图像素，中心位于 `pv0+5.5`（比槽底正中低 0.5 像素），宽度按蒙版实际宽高比推导。
