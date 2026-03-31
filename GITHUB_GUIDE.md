# GitHub 上传指南

## 方法一：使用 GitHub 网页（推荐新手）

### 1. 创建仓库

1. 登录 GitHub: https://github.com
2. 点击右上角 **+** → **New repository**
3. 填写信息：
   - Repository name: `dva`
   - Description: `安卓端侧行车记录仪违章分析系统`
   - 选择 **Private** 或 **Public**
   - **不要**勾选 Add a README file（我们已有）
4. 点击 **Create repository**

### 2. 上传文件

1. 在新建的仓库页面，点击 **uploading an existing file**
2. 将 DVA 文件夹中的所有文件拖入上传区域
3. 点击 **Commit changes**

### 3. 完成！

仓库地址: `https://github.com/birdnofoots/dva`

---

## 方法二：使用 Git 命令行

### 1. 在 GitHub 创建仓库后，复制仓库地址

```
https://github.com/birdnofoots/dva.git
```

### 2. 在本地初始化 Git 并推送

```bash
# 进入项目目录
cd DVA

# 初始化 Git
git init

# 添加所有文件
git add .

# 提交
git commit -m "Initial commit: DVA - DashCam Violation Analyzer"

# 添加远程仓库
git remote add origin https://github.com/birdnofoots/dva.git

# 推送（首次推送需要认证）
git push -u origin master
```

### 3. 认证方式

#### 使用 Personal Access Token (推荐)

1. GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. 点击 **Generate new token**
3. 勾选 `repo` 权限
4. 复制生成的 token

推送时用户名输入 `birdnofoots`，密码输入 token

#### 或使用 SSH

1. 生成 SSH Key:
   ```bash
   ssh-keygen -t ed25519 -C "birdnofoots@gmail.com"
   ```
2. 复制公钥到 GitHub Settings → SSH Keys
3. 修改远程仓库地址:
   ```bash
   git remote set-url origin git@github.com:birdnofoots/dva.git
   ```
4. 推送:
   ```bash
   git push -u origin master
   ```

---

## 方法三：使用 GitHub CLI

```bash
# 安装 gh cli
brew install gh  # macOS
# 或从 https://cli.github.com 安装

# 登录
gh auth login

# 创建仓库并推送
cd DVA
gh repo create dva --public --source=. --remote=origin
```

---

## 下载模型后提交

如果之前上传时模型未下载，之后下载模型后：

```bash
cd DVA

# 更新文件
git add .
git commit -m "Add ML models"
git push origin master
```

---

## 常见问题

### Q: 推送被拒绝？

A: 可能原因：
1. 远程仓库已有文件冲突 → `git pull --rebase origin master`
2. 认证失败 → 检查用户名和密码/Token
3. 分支名不对 → 确保在 master/main 分支

### Q: 文件太大被拒绝？

A: 模型文件较大，确保 `.gitignore` 正确，并使用 Git LFS:
```bash
git lfs install
git lfs track "*.onnx"
git add .gitattributes
git add .
git commit -m "Add models with LFS"
```

### Q: 忽略某些文件

已配置 `.gitignore` 忽略：
- `build/` - 构建输出
- `.gradle/` - Gradle 缓存
- `app/build/` - APK 输出
- `.idea/` - IDE 配置

---

## 设置仓库描述和标签

1. 进入仓库主页
2. 点击 ⚙️ Settings
3. 设置：
   - Description: `安卓端侧行车记录仪违章分析系统`
   - Website: 可选
4. 添加 Topics: `android`, `kotlin`, `machine-learning`, `dashcam`, `traffic-violation`

---

祝你上传顺利！有问题可以提交 Issue。
