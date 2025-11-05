#!/bin/bash
# Monitor CI run until completion

echo "Monitoring CI run..."
echo ""

for i in {1..30}; do
    sleep 10

    result=$(curl -s "https://api.github.com/repos/nabacg/lasm/actions/runs?branch=claude/wip-pong-demo-011CUpXxyCnPkAQowgjSdJcv&per_page=1" | python3 -c "
import sys, json
d = json.load(sys.stdin)
r = d['workflow_runs'][0] if d['workflow_runs'] else {}
print(f\"{r.get('status', 'unknown')}|{r.get('conclusion', 'none')}\")
" 2>/dev/null)

    status="${result%|*}"
    conclusion="${result#*|}"

    echo "[$i] Status: $status | Conclusion: $conclusion"

    if [ "$status" = "completed" ]; then
        echo ""
        echo "✓ CI completed with: $conclusion"
        exit 0
    fi
done

echo ""
echo "Timeout waiting for CI"
exit 1
