"""Adds Claude's judgements of web photos to the gold file. Lines "n|verdict|kind|words" (n = the number on
the india-sets sheets) on stdin; the photo's id, set and community come from india.jsonl. A photo already
judged keeps its first judgement.   python3 -I gold_add.py INDIA.jsonl GOLD.tsv [community for these sets, e.g. W042=uk-gujarati] < lines"""
import json, sys
india = {r['n']: r for r in (json.loads(l) for l in open(sys.argv[1]))}
named = dict(a.split('=') for a in sys.argv[3:])
have = {l.split('\t')[0] for l in open(sys.argv[2], encoding='utf-8') if not l.startswith('#')}
added = 0
with open(sys.argv[2], 'a', encoding='utf-8') as w:
    for l in sys.stdin:
        l = l.strip()
        if not l or l.startswith('#'): continue
        n, v, kind, words = l.split('|', 3); r = india[int(n)]
        assert v in '012' and kind and words, l
        if r['photo'] in have: continue
        w.write(f"{r['photo']}\t{r['set']}\t{named.get(r['set'], r['known'] or '?')}\t{v}\t{kind.strip()}\t{words.strip()}\n"); have.add(r['photo']); added += 1
print(added, 'added;', len(have), 'photos judged in all')
