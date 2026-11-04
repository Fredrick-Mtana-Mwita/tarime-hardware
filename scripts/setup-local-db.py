from pathlib import Path
import argparse,json,os,secrets,socket,subprocess

parser=argparse.ArgumentParser(description='Create/start only the project-owned local PostgreSQL cluster; never modifies an existing server.')
parser.add_argument('--pg-bin',default='D:/Program Files/Postgress/bin')
parser.add_argument('--port',type=int,default=55432)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
local=root/'.local';local.mkdir(exist_ok=True)
(local/'tmp').mkdir(exist_ok=True)
data=local/'postgres-data';marker=local/'postgres-instance.json'
pg=Path(args.pg_bin).resolve()
if not (pg/'initdb.exe').exists(): raise SystemExit('Pass --pg-bin pointing to an existing PostgreSQL installation.')
def run(command,**kwargs):
 return subprocess.run([str(x) for x in command],check=True,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0),**kwargs)
if marker.exists():
 state=json.loads(marker.read_text())
 if Path(state['data']).resolve()!=data.resolve(): raise SystemExit('Unexpected data path; refusing reuse.')
 if state['port']!=args.port: raise SystemExit('Use the previously configured port.')
 if not state.get('provisioned',False): raise SystemExit('Database setup is incomplete; inspect the project instance before restarting provisioning.')
 pg=Path(state['bin'])
 status=subprocess.run([str(pg/'pg_ctl.exe'),'-D',str(data),'status'],capture_output=True)
 if status.returncode:
  run([pg/'pg_ctl.exe','-D',data,'-l',local/'postgres.log','-w','start'])
 print('Project database instance is running. Existing credentials preserved.')
 raise SystemExit(0)
if data.exists(): raise SystemExit('Unrecorded data directory exists; inspect it instead of overwriting.')
for envfile in ['.env','.env.test']:
 if (root/'backend'/envfile).exists():raise SystemExit('Existing backend credential file preserved; setup stopped.')
with socket.socket() as probe:
 try:probe.bind(('127.0.0.1',args.port))
 except OSError:raise SystemExit('Selected port is occupied. Choose a free --port; no existing server will be changed.')
admin=secrets.token_hex(24);dev=secrets.token_hex(24);test=secrets.token_hex(24)
pw=local/'initdb-password'
pw.write_text(admin+'\n',encoding='utf-8')
try:run([pg/'initdb.exe','-D',data,'-U','finishers_local_admin','--encoding=UTF8','--locale=C','--auth=scram-sha-256','--pwfile',pw],stdout=subprocess.DEVNULL)
finally:pw.unlink(missing_ok=True)
with (data/'postgresql.conf').open('a',encoding='utf-8') as f:
 f.write("\nlisten_addresses = '127.0.0.1'\nport = "+str(args.port)+"\npassword_encryption = 'scram-sha-256'\n")
# Record ownership before start, allowing a failed startup to be inspected/restarted safely.
state={'data':str(data),'bin':str(pg),'port':args.port,'provisioned':False}
marker.write_text(json.dumps(state,indent=2),encoding='utf-8')
(local/'admin-password').write_text(admin,encoding='utf-8')
run([pg/'pg_ctl.exe','-D',data,'-l',local/'postgres.log','-w','start'])
sql=local/'bootstrap.sql'
sql.write_text(f"""CREATE ROLE finishers_dev LOGIN PASSWORD '{dev}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE finishers_test LOGIN PASSWORD '{test}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE DATABASE finishers_dev OWNER finishers_dev;
CREATE DATABASE finishers_test OWNER finishers_test;
REVOKE ALL ON DATABASE finishers_dev FROM PUBLIC;
REVOKE ALL ON DATABASE finishers_test FROM PUBLIC;
GRANT CONNECT ON DATABASE finishers_dev TO finishers_dev;
GRANT CONNECT ON DATABASE finishers_test TO finishers_test;
""",encoding='utf-8')
env=os.environ.copy();env['PGPASSWORD']=admin
try:run([pg/'psql.exe','-X','-w','-h','127.0.0.1','-p',args.port,'-U','finishers_local_admin','-d','postgres','-v','ON_ERROR_STOP=1','-f',sql],env=env,stdout=subprocess.DEVNULL)
finally:sql.unlink(missing_ok=True)
for name,password in [('dev',dev),('test',test)]:
 prefix='FPA_DB' if name=='dev' else 'FPA_TEST_DB'
 content=f'{prefix}_URL=jdbc:postgresql://127.0.0.1:{args.port}/finishers_{name}\n{prefix}_USER=finishers_{name}\n{prefix}_PASSWORD={password}\n'
 (root/'backend'/('.env' if name=='dev' else '.env.test')).write_text(content,encoding='utf-8')
 # Prove that the application roles connect only to their own databases.
 env['PGPASSWORD']=password
 run([pg/'psql.exe','-X','-w','-h','127.0.0.1','-p',args.port,'-U','finishers_'+name,'-d','finishers_'+name,'-tAc','select current_database()'],env=env,stdout=subprocess.DEVNULL)
 other='test' if name=='dev' else 'dev'
 denied=subprocess.run([str(pg/'psql.exe'),'-X','-w','-h','127.0.0.1','-p',str(args.port),'-U','finishers_'+name,'-d','finishers_'+other,'-tAc','select 1'],env=env,capture_output=True)
 if denied.returncode==0:raise SystemExit('Cross-database isolation failed.')
state['provisioned']=True
marker.write_text(json.dumps(state,indent=2),encoding='utf-8')
print('Created project-only dev/test databases and separate non-superuser roles. Verified cross-database access is denied.')
print('Private credentials saved in ignored backend/.env and backend/.env.test. No credentials printed.')
