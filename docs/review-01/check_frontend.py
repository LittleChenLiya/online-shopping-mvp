"""仅验证公开前端文件与JavaScript语法，不冒充完整业务测试。"""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
folder=root/'01_前端界面'
for name in ('index.html','style.css','app.js','README.md'):
    if not (folder/name).is_file():
        raise SystemExit('Missing frontend file: '+name)
try:
    result=subprocess.run(['node','--check',str(folder/'app.js')],check=False)
except FileNotFoundError:
    raise SystemExit('Install Node.js before running the syntax check.')
raise SystemExit(result.returncode)
