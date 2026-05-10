#!/bin/bash
cd /home/KeyboardBlur

# 1. 删掉 Termux 目录下的残留 lock 文件和旧 .git
rm -f /data/data/com.termux/files/home/.git/index.lock

# 2. 检查项目目录有没有自己的 .git
if [ -d .git ]; then
    echo "项目目录已有 .git，跳过初始化"
else
    echo "初始化 git 仓库..."
    git init
    git branch -M main
fi

# 3. 配置 remote（如果还没有的话）
if ! git remote get-url origin &>/dev/null; then
    echo "添加 remote origin..."
    git remote add origin https://github.com/jianyu881129/GlassKey.git
else
    echo "remote 已存在: $(git remote get-url origin)"
fi

# 4. 提交并推送
git add -A
git commit -m "feat: 集成 Kawase GPU 模糊模块，双引擎架构" --allow-empty
git push -u origin main --force

echo "=== done ==="
