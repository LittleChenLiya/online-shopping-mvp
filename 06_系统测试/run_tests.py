"""一键执行原有6组回归和新增8组验收测试，生成真实日志与报告。"""
import io
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import unittest
from datetime import datetime, timedelta, timezone

sys.dont_write_bytecode = True
BASE = Path(__file__).resolve().parent
ROOT = BASE.parent
sys.path.insert(0, str(BASE))
if os.name == 'nt' and Path('D:/').exists():
    os.environ.setdefault('SHOP_TEST_TEMP_DIR', 'D:/Codex/work/shopping-tests-06')
import test_acceptance


class RecordedResult(unittest.TextTestResult):
    """在unittest正常判定之外记录每个测试及子测试的实际结果。"""
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.records = {}

    def addSuccess(self, test):
        super().addSuccess(test)
        self.records[test.id()] = {'status': 'PASS', 'detail': ''}

    def addFailure(self, test, err):
        super().addFailure(test, err)
        self.records[test.id()] = {'status': 'FAIL', 'detail': self._exc_info_to_string(err, test)}

    def addError(self, test, err):
        super().addError(test, err)
        self.records[test.id()] = {'status': 'ERROR', 'detail': self._exc_info_to_string(err, test)}

    def addSubTest(self, test, subtest, err):
        super().addSubTest(test, subtest, err)
        if err is not None:
            self.records[test.id()] = {'status': 'FAIL', 'detail': self._exc_info_to_string(err, test)}


def main():
    parser = argparse.ArgumentParser(description='运行系统测试并记录真实执行信息')
    parser.add_argument('--executor', default='', help='本次执行负责人姓名')
    parser.add_argument('--execution-mode', default='', help='例如本人运行或现场AI辅助操作')
    args = parser.parse_args()
    input_files = [p for folder in ROOT.iterdir() if folder.is_dir() and folder.name[:2] in
                   ('01', '02', '03', '04', '05', '06') for p in folder.glob('*')
                   if p.is_file() and p.suffix in ('.py', '.js', '.html', '.css')]
    input_hashes = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                    for p in sorted(input_files)}
    suite = unittest.TestSuite([
        unittest.defaultTestLoader.loadTestsFromTestCase(test_acceptance.regression.ShoppingTest),
        unittest.defaultTestLoader.loadTestsFromTestCase(test_acceptance.AcceptanceTests),
    ])
    stream = io.StringIO()
    result = unittest.TextTestRunner(stream=stream, verbosity=2, resultclass=RecordedResult).run(suite)
    log = stream.getvalue()
    when = datetime.now(timezone(timedelta(hours=8))).isoformat(timespec='seconds')
    output = BASE / 'results'
    output.mkdir(exist_ok=True)
    (output / 'test-log.txt').write_text(log, encoding='utf-8')
    passed = sum(v['status'] == 'PASS' for v in result.records.values())
    summary = {'time': when, 'python': sys.version.split()[0], 'total': result.testsRun,
               'passed': passed, 'failures': len(result.failures), 'errors': len(result.errors),
               'success': result.wasSuccessful(), 'cases': result.records,
               'executor': args.executor, 'execution_mode': args.execution_mode,
               'input_sha256': input_hashes}
    (output / 'test-results.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    lines = [
        '# 系统测试执行报告', '', f'执行时间：{when}（Asia/Shanghai）',
        f'Python：{summary["python"]}；测试对象：同级01～05模块当前本机文件。',
        '', f'共{result.testsRun}组：通过{passed}，失败{len(result.failures)}，错误{len(result.errors)}。',
        '', '范围：复用第五模块6组回归测试，新增第六模块8组跨模块验收测试。',
        '结果只适用于本次本机代码，不证明其他提交版本、浏览器交互或公网部署已经通过。',
        '', '| 测试方法 | 来源 | 结果 |', '|---|---|---|',
    ]
    for name, value in sorted(result.records.items()):
        origin = '新增验收' if 'AcceptanceTests' in name else '复用原有回归'
        lines.append(f'| {name.rsplit(".", 1)[-1]} | {origin} | {value["status"]} |')
    lines += ['', '## 待人工验证', '',
              '浏览器图片预览、表单提示、取消确认、快速切页和手机布局尚需手工验证。',
              '本报告和日志不是运行录屏；课堂要求的视频需要成员真实执行并录制。',
              '任务负责人：庄云月；学号：2210020119。',
              f'本次执行负责人：{args.executor or "未登记"}；执行方式：{args.execution_mode or "未登记"}。',
              f'本次执行完成时间：{when}；断言与隔离方式复核另见复核记录。',
              '', '失败详情见 test-log.txt 与 test-results.json。']
    (output / '测试报告.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(log)
    print(f'结果目录：{output}')
    return 0 if result.wasSuccessful() else 1


if __name__ == '__main__':
    raise SystemExit(main())
