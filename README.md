# 墨笺 (InkNote)

**Android Markdown 阅读器** — Kotlin + Jetpack Compose + WebView

专为学术阅读设计，支持 LaTeX 数学公式渲染、Mermaid 图表、TTS 朗读、AI 翻译、高亮标注。

## 功能

- **Markdown 渲染** — marked.js 引擎，支持表格、代码块、块引用
- **LaTeX 数学公式** — KaTeX，行内 `$...$` 和块级 `$$...$$`
- **Mermaid 图表** — 流程图、序列图、类图、状态图
- **TTS 朗读** — 段落级高亮跟随 + 自动滚动
- **AI 翻译** — 选中文字即时翻译
- **高亮标注** — 划词高亮，持久化存储，面板管理
- **全屏阅读** — 沉浸模式，浮动退出按钮
- **主题切换** — 亮色 / 暗色 / 护眼
- **文档内搜索** — 跨节点全文匹配
- **大纲导航** — 标题联动跳转
- **书签** — 段落级书签，持久化
- **字体缩放** — 8~24px 竖向滑块
- **手机/平板自适应** — MobileLayout + TabletLayout

## 技术栈

| 层 | 技术 |
|:---|:---|
| UI | Jetpack Compose + Material 3 |
| 渲染 | WebView + marked.js + KaTeX + Mermaid |
| TTS | Android TextToSpeech |
| 存储 | SharedPreferences + ContentResolver |
| 翻译 | DeepSeek API |
| 构建 | Gradle 8.12 + Kotlin 1.9 |

## 代码统计

- Kotlin: 2,345 行 (17 文件)
- XML: 160 行 (6 文件)
- HTML/JS/CSS: 627 行

## 版本

| 版本 | 日期 | 要点 |
|:---|:---|:---|
| v2.4.3 | 2025-05-31 | 🎯 稳定版 · 修复图标 + LaTeX 公式渲染 |
| v2.4.2 | 2025-05-31 | 控制字符还原修复 |
| v2.0.0 | 2025-05 | 主题/搜索/书签/翻译 |
| v1.0.0 | 2025-05 | 基础 WebView 渲染 |

## 下载

[📦 最新 APK](https://github.com/gzmliang/mojian/releases/latest)

## 作者

[gzmliang](https://github.com/gzmliang)
