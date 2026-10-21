"""检查 Python 语法并打包可解压运行的基线版本，不包括数据库和凭据。"""
import argparse
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODULES = (
    '01_前端界面', '02_商品浏览与购买意向', '03_商品发布与历史',
    '04_交易与冻结管理', '05_卖家账号与系统集成', '06_系统测试',
)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True, help='构建结果目录')
    args = parser.parse_args()
    version = (ROOT / 'VERSION').read_text(encoding='utf-8').strip()
    files = [ROOT / name for name in ('start.py', '启动系统.bat', 'README.md', 'VERSION', 'Dockerfile')]
    files.extend(ROOT / name for name in ('第一次汇报.pptx', '在线购物系统需求规格说明书.docx')
                 if (ROOT / name).is_file())
    for module in MODULES:
        files.extend(p for p in (ROOT / module).rglob('*')
                     if p.is_file() and p.suffix in ('.py', '.html', '.css', '.js', '.md', '.txt', '.json')
                     and '__pycache__' not in p.parts)
    for file in files:
        if file.suffix == '.py':
            compile(file.read_text(encoding='utf-8'), str(file), 'exec')
    args.output.mkdir(parents=True, exist_ok=True)
    target = args.output / f'online-shopping-mvp-{version}-build.zip'
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as package:
        for file in sorted(files):
            package.write(file, f'online-shopping-mvp-{version}/{file.relative_to(ROOT).as_posix()}')
    print(f'构建成功：{target}，{len(files)} 个文件')


if __name__ == '__main__':
    main()
