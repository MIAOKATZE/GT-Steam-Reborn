"""Summarize each native structure result; COMPLETE or runner PASS alone is insufficient."""
from pathlib import Path
import argparse
import collections
import csv
import hashlib
import json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reports', type=Path, nargs='+')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    rows = []
    reports = []
    for path in args.reports:
        data = path.read_bytes()
        report = json.loads(data.decode('utf-8-sig'))
        planned_ids = report.get('plannedTasks', [])
        result_ids = [row['id'] for row in report['results']]
        ids_match = (len(set(planned_ids)) == len(planned_ids)
                     and len(set(result_ids)) == len(result_ids)
                     and set(planned_ids) == set(result_ids))
        reports.append({'file': str(path.resolve()), 'sha256': hashlib.sha256(data).hexdigest(),
                        'runId': report['runId'], 'state': report['state'], 'gtsrVersion': report.get('gtsrVersion'),
                        'gtVersion': report.get('gtVersion'), 'planned_tasks': len(planned_ids),
                        'planned_result_ids_match': ids_match})
        for row in report['results']:
            rows.append(dict(row, runId=report['runId']))
    counts = dict(collections.Counter(row['status'] for row in rows))
    passed = (bool(rows) and all(r['state'] == 'COMPLETE' and r['planned_result_ids_match'] for r in reports)
              and all(row['status'] == 'PASS' for row in rows))
    planned = sum(r['planned_tasks'] for r in reports)
    passed = passed and len(rows) == planned
    summary = {'reports': reports, 'result_count': len(rows), 'unique_case_count': len({r['id'] for r in rows}),
               'status_counts': counts, 'all_cases_pass': passed,
               'non_pass': [r for r in rows if r['status'] != 'PASS']}
    (args.output / 'summary.json').write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding='utf-8')
    with (args.output / 'per-case.csv').open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.writer(stream)
        writer.writerow(['runId', 'id', 'status', 'detail', 'details'])
        for row in rows:
            writer.writerow([row['runId'], row['id'], row['status'], row['detail'],
                             json.dumps(row.get('details', {}), ensure_ascii=False)])
    print(json.dumps({k: summary[k] for k in ('result_count', 'unique_case_count', 'status_counts', 'all_cases_pass')}))
    raise SystemExit(0 if passed else 1)


if __name__ == '__main__':
    main()
