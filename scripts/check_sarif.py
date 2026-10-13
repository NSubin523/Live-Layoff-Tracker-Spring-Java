"""Fail CI on high/critical security findings in CodeQL's SARIF reports."""
import json
import sys
from pathlib import Path


def check_reports(directory):
    reports = list(Path(directory).rglob('*.sarif'))
    if not reports:
        raise ValueError('CodeQL produced no SARIF reports')
    findings = []
    for report in reports:
        document = json.loads(report.read_text())
        if document.get('version') != '2.1.0' or not document.get('runs'):
            raise ValueError(f'Invalid SARIF report: {report.name}')
        for run in document['runs']:
            components = [run['tool']['driver'], *run['tool'].get('extensions', [])]
            for result in run.get('results', []):
                reference = result.get('rule', {})
                component_ref = reference.get('toolComponent', {})
                if 'index' in component_ref:
                    component = run['tool'].get('extensions', [])[component_ref['index']]
                    candidates = [component]
                elif 'name' in component_ref:
                    candidates = [c for c in components if c.get('name') == component_ref['name']]
                else:
                    candidates = components
                rule_id = reference.get('id', result.get('ruleId'))
                matches = [rule for c in candidates for rule in c.get('rules', [])
                           if rule_id is not None and rule['id'] == rule_id]
                rule = matches[0] if len(matches) == 1 else None
                index = reference.get('index', result.get('ruleIndex'))
                if rule is None and rule_id is None and index is not None:
                    # Legacy ruleIndex belongs to the driver; a descriptor
                    # reference may instead select an extension component.
                    rules = (candidates[0] if component_ref else components[0]).get('rules', [])
                    rule = rules[index]
                if rule is None:
                    raise ValueError('Finding has no matching rule metadata')
                severity = rule.get('properties', {}).get('security-severity')
                if severity is not None and float(severity) >= 7:
                    findings.append(rule['id'])
    return findings


if __name__ == '__main__':
    findings = check_reports(sys.argv[1])
    if findings:
        raise SystemExit('High/critical CodeQL findings: ' + ', '.join(sorted(set(findings))))
    print('No high/critical CodeQL security findings')
