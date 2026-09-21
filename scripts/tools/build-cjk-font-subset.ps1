# ======================================================================
# 生成前端自带的 CJK 子集字体 (Stage 10-P)
#
# 为什么需要这个脚本：
#   Flutter Web（CanvasKit）的中日韩字形默认在**运行时**从 fonts.gstatic.com 拉取，
#   校园网/国内网络访问不到 Google 时，界面上的汉字会全部渲染成方块（tofu）。
#   解决办法是自带一份本地 CJK 字体并设为全局字体族（见 lib/core/theme/app_theme.dart）。
#
#   本脚本把 Noto Sans SC（SIL OFL 1.1）裁剪成项目实际需要的子集：
#   源字体 16.9MB → 实例化到 Regular 10.1MB → 子集约 1.8MB。
#
# 依赖（本机一次性准备，走国内镜像）：
#   python -m pip install --user -i https://mirrors.aliyun.com/pypi/simple/ fonttools jieba
#
# 用法：
#   powershell -File scripts/tools/build-cjk-font-subset.ps1
#   # 可选参数：-SourceFont <源字体路径> -IncludeDatabase（额外收录线上库里的真实文本用字）
#
# 注意：
#   1. 子集是**修改版**，按 OFL 的保留字体名条款不能沿用官方字体名，
#      脚本会把内部字体名改成中性名（CampusLibraryCJK）；
#   2. 许可文件 assets/fonts/OFL-1.1.txt 必须随字体一起分发；
#   3. 若将来出现"生僻字显示为方块"，说明该字不在子集内 ——
#      把出现该字的语料目录加进 -CorpusDirs，或在脚本里提高 -TopChars 后重跑。
# ======================================================================
param(
    [string]$SourceFont = "C:\Windows\Fonts\NotoSansSC-VF.ttf",
    [string]$OutFile = "",
    [int]$TopChars = 6000,
    [string[]]$CorpusDirs = @(),
    [switch]$IncludeDatabase
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path "$PSScriptRoot\..\..").Path
if (-not $OutFile) {
    $OutFile = Join-Path $repoRoot "frontend\assets\fonts\CampusLibraryCJK-Subset-Regular.ttf"
}

if (-not (Test-Path $SourceFont)) {
    Write-Host "[错误] 找不到源字体: $SourceFont" -ForegroundColor Red
    Write-Host "       可从 https://fonts.google.com/noto/specimen/Noto+Sans+SC 获取（SIL OFL 1.1）" -ForegroundColor Yellow
    exit 1
}

Write-Host "源字体: $SourceFont"
Write-Host "输出到: $OutFile"
Write-Host ""

# 把参数传给内嵌的 Python 脚本（字体处理只能用 fontTools）
$py = @'
import os, io, sys, glob, collections, subprocess

src, out, top_n = sys.argv[1], sys.argv[2], int(sys.argv[3])
corpus_dirs = [d for d in sys.argv[4:] if d]
repo = os.environ.get("CJK_REPO_ROOT", ".")

try:
    import jieba
    from fontTools.ttLib import TTFont
    from fontTools.varLib.instancer import instantiateVariableFont
    from fontTools import subset
except ImportError as e:
    print(f"[错误] 缺少依赖: {e}\n       请先执行: python -m pip install --user -i https://mirrors.aliyun.com/pypi/simple/ fonttools jieba", file=sys.stderr)
    sys.exit(2)

CJK = lambda o: 0x4E00 <= o <= 0x9FFF or 0x3400 <= o <= 0x4DBF or 0xF900 <= o <= 0xFAFF

def add_text(s, acc):
    for ch in s:
        o = ord(ch)
        if CJK(o) or 0x3000 <= o <= 0x303F or 0xFF00 <= o <= 0xFFEF:
            acc.add(ch)

# 1) 词频前 N 的常用汉字：保证"没有进过语料"的常见字也能显示
freq = collections.Counter()
with io.open(os.path.join(os.path.dirname(jieba.__file__), "dict.txt"), encoding="utf-8") as f:
    for line in f:
        p = line.split()
        if len(p) < 2:
            continue
        try:
            fq = int(p[1])
        except ValueError:
            continue
        for ch in p[0]:
            if CJK(ord(ch)):
                freq[ch] += fq
chars = {c for c, _ in freq.most_common(top_n)}
print(f"  词频前 {top_n} 常用字: {len(chars)}")

# 2) 仓库内全部用字（源码/文档/迁移脚本）
before = len(chars)
patterns = ["frontend/lib/**/*.dart", "backend/src/main/java/**/*.java",
            "backend/src/main/resources/db/migration/*.sql", "docs/*.md", "README.md"]
for pat in patterns:
    for p in glob.glob(os.path.join(repo, pat), recursive=True):
        try:
            add_text(io.open(p, encoding="utf-8", errors="ignore").read(), chars)
        except Exception:
            pass
print(f"  叠加仓库用字: +{len(chars)-before}")

# 3) 可选：线上库真实文本（书名/简介/昵称等动态内容最容易漏）
if os.environ.get("CJK_INCLUDE_DB") == "1":
    before = len(chars)
    for q in ["select title from books", "select coalesce(author,'') from books",
              "select coalesce(description,'') from books", "select nickname from users",
              "select coalesce(title,'') || coalesce(content,'') from notifications",
              "select name from categories"]:
        r = subprocess.run(["docker", "exec", "-i", "campus-library-postgres", "psql",
                            "-U", "library", "-d", "library_system", "-tAc", q],
                           capture_output=True)
        add_text(r.stdout.decode("utf-8", "ignore"), chars)
    print(f"  叠加线上库文本: +{len(chars)-before}")

# 4) 可选：额外中文语料目录（提升罕见字覆盖）
if corpus_dirs:
    before = len(chars)
    for d in corpus_dirs:
        for p in glob.glob(os.path.join(d, "**", "*.md"), recursive=True)[:500]:
            try:
                add_text(io.open(p, encoding="utf-8", errors="ignore").read(), chars)
            except Exception:
                pass
    print(f"  叠加额外语料: +{len(chars)-before}")

# 5) 基础字符
for o in range(0x20, 0x7F):
    chars.add(chr(o))
for o in range(0xFF01, 0xFF5F):
    chars.add(chr(o))
chars.add("\u3000")
print(f"  目标字符总数: {len(chars)}")

# 6) 可变字体 → 静态 Regular（体积先砍掉约 40%）
font = TTFont(src)
instantiateVariableFont(font, {"wght": 400}, inplace=True, updateFontNames=True)
static_path = out + ".static.tmp.ttf"
font.save(static_path)
font.close()

# 7) 子集裁剪（保留 name ID 0/13/14：版权与许可声明，OFL 要求随副本保留）
subset.main([static_path, "--text=" + "".join(sorted(chars)), "--output-file=" + out,
             "--layout-features=", "--no-hinting", "--drop-tables+=DSIG",
             "--name-IDs=0,1,2,3,4,5,6,13,14", "--recalc-bounds"])
os.remove(static_path)

# 8) 按 OFL 保留字体名条款改名（子集属修改版，不得沿用官方字体名）
sub = TTFont(out)
for rec in sub["name"].names:
    if rec.nameID == 1: rec.string = "CampusLibraryCJK"
    elif rec.nameID == 3: rec.string = "CampusLibrary-CJK-Subset-1.0"
    elif rec.nameID == 4: rec.string = "CampusLibrary CJK Subset (based on Noto Sans SC, OFL-1.1)"
    elif rec.nameID == 6: rec.string = "CampusLibraryCJK-Subset"
    elif rec.nameID == 16: rec.string = "CampusLibraryCJK"
sub.save(out)

print(f"  完成: {out}")
print(f"  体积: {os.path.getsize(out)/1024/1024:.2f} MB, 字形数: {len(TTFont(out).getGlyphOrder())}")
'@

$pyFile = Join-Path $env:TEMP "build_cjk_subset.py"
$py | Out-File -FilePath $pyFile -Encoding utf8
$env:CJK_REPO_ROOT = $repoRoot
if ($IncludeDatabase) { $env:CJK_INCLUDE_DB = "1" } else { $env:CJK_INCLUDE_DB = "0" }

python $pyFile $SourceFont $OutFile $TopChars @CorpusDirs
if ($LASTEXITCODE -ne 0) {
    Write-Host "[失败] 生成子集字体未成功，退出码 $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "[完成] 子集字体已生成。请确认：" -ForegroundColor Green
Write-Host "  - frontend/assets/fonts/OFL-1.1.txt 仍在（OFL 要求随字体分发许可）"
Write-Host "  - 重新构建前端产物生效: flutter build web --release"
