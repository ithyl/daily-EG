#!/usr/bin/env python3
"""Offline structural checks (not a substitute for Maven/MySQL/integration tests)."""
import json, pathlib, re
ROOT=pathlib.Path(__file__).resolve().parents[1]
source=json.loads((ROOT/'server/src/main/resources/seed/lessons.json').read_text(encoding='utf-8'))
frontend=json.loads((ROOT/'web/public/lessons.json').read_text(encoding='utf-8'))
assert len(source)==len(frontend)==8
assert [x['day'] for x in source]==list(range(22,30))
for s, f in zip(source, frontend):
    assert s['title']==f['title']
    assert s['segments']==f['segments']
    assert all(x['en'].strip() and x['zh'].strip() for x in s['segments'])
    assert len(s['words'])>=8
assert (ROOT/'server/src/main/resources/db/migration/V1__schema.sql').exists()
assert (ROOT/'server/src/main/resources/db/migration/V2__production_features.sql').exists()
assert (ROOT/'server/src/main/java/com/speakflow/service/LessonGenerationService.java').exists()
assert (ROOT/'server/src/main/java/com/speakflow/ai/OpenAIClient.java').exists()
assert (ROOT/'web/src/recorder.ts').exists()
assert not list(ROOT.glob('**/.env'))
assert 'APP_ADMIN_PASSWORD' in (ROOT/'compose.yaml').read_text()
for p in ROOT.glob('**/*.java'):
    assert not re.search(r'Bearer\s+sk-[A-Za-z0-9]+',p.read_text(encoding='utf-8'))
print('PASS: 8 bilingual lessons, mirrored seed data, additive migrations, expected code, no env secrets')
