# 发布到 GitHub Releases（完整流程）

本文说明如何将预编译 APK 发布到本仓库的 **Releases** 页面。APK 体积较大，**不要** 提交进 Git 历史（仓库已 `.gitignore` 忽略 `*.apk`），应作为 **Release 附件** 上传。

## 前置条件

- 已安装 [GitHub CLI](https://cli.github.com/)：`gh`，并完成 `gh auth login`
- 或对仓库有 **Maintainer/Admin** 权限，使用网页上传
- 本地已有待发布的 APK（例如根目录 `OpenKiwi322.apk`，或 `app/build/outputs/apk/debug/app-debug.apk`）

## 方式一：命令行（推荐）

在项目根目录执行（按你的实际文件路径修改）：

```powershell
# Windows PowerShell — 示例：发布 v3.2.2，附带 OpenKiwi322.apk
$tag = "v3.2.2"
$apk = ".\OpenKiwi322.apk"   # 或 .\app\build\outputs\apk\debug\app-debug.apk

gh release create $tag $apk `
  --repo HuSuuuu/OpenKiwi `
  --title "OpenKiwi 3.2.2" `
  --notes-file .\docs\RELEASE_NOTES_v3.2.2.md `
  --latest
```

若 APK 不在当前目录：

```powershell
gh release create v3.2.2 "C:\path\to\OpenKiwi322.apk" --repo HuSuuuu/OpenKiwi --title "OpenKiwi 3.2.2" --notes-file .\docs\RELEASE_NOTES_v3.2.2.md
```

**指定附件在 Release 上的显示名**（可选）：

```powershell
gh release create v3.2.2 "OpenKiwi322.apk#OpenKiwi-3.2.2-debug.apk" --repo HuSuuuu/OpenKiwi --title "OpenKiwi 3.2.2" --notes-file .\docs\RELEASE_NOTES_v3.2.2.md
```

### 仅更新已有 Release 的资产

```powershell
gh release upload v3.2.2 OpenKiwi322.apk --repo HuSuuuu/OpenKiwi --clobber
```

## 方式二：GitHub 网页

1. 打开：<https://github.com/HuSuuuu/OpenKiwi/releases>
2. 点击 **Draft a new release**
3. **Choose a tag**：新建标签，例如 `v3.2.2`
4. **Release title**：例如 `OpenKiwi 3.2.2`
5. 描述：复制 `docs/RELEASE_NOTES_v3.2.2.md` 内容，或自行编写
6. **Attach binaries**：拖拽 `OpenKiwi322.apk`
7. 勾选 **Set as the latest release**（若适用）
8. 发布 **Publish release**

## 与 README 同步

发版后请在 `README.md` 顶部的「下载」区确认：

- 指向最新 **Releases** 链接
- 写明当前推荐版本号与文件名（如 `OpenKiwi322.apk`）

## 签名与正式渠道（可选）

当前文档以 **debug / 未签名** 或本地构建包为例。若上架 Google Play 或大规模分发，请：

- 使用 **release** 构建 + **签名配置**
- 在 Release 说明中标注 **SHA256** 校验和，便于用户校验

生成 SHA256（PowerShell）：

```powershell
Get-FileHash .\OpenKiwi322.apk -Algorithm SHA256
```

将结果写入 Release 说明。

## 常见问题

| 问题 | 处理 |
|------|------|
| `gh: not found` | 安装 GitHub CLI 并重启终端 |
| `HTTP 403` | 确认对仓库有写权限、Token 含 `repo` |
| 上传中断 | 使用 `gh release upload ... --clobber` 重试 |
| APK 超过 2GB | GitHub 单文件上限 2GB；超限需拆分或使用其他分发 |
