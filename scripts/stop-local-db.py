from pathlib import Path
import json, subprocess
root=Path(__file__).resolve().parents[1]
marker=root/'.local/postgres-instance.json'
if not marker.exists(): raise SystemExit('No project-owned database instance recorded.')
state=json.loads(marker.read_text())
data=root/'.local/postgres-data'
if Path(state['data']).resolve()!=data.resolve(): raise SystemExit('Unexpected data directory; stopping is refused.')
subprocess.run([str(Path(state['bin'])/'pg_ctl.exe'),'-D',str(data),'-w','stop','-m','fast'],check=True)
