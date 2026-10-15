"""项目启动入口。实际 HTTP 服务属于第五位成员。"""
import runpy
import sys
from pathlib import Path

folder = Path(__file__).resolve().parent / '05_卖家账号与系统集成'
sys.path.insert(0, str(folder))
runpy.run_path(str(folder / 'server.py'), run_name='__main__')
