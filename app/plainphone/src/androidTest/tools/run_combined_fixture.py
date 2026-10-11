# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned-AVD-only synthetic provider fixtures; counts-only output. Never ships."""
import os,subprocess,re,json,pathlib,sys
adb=os.path.expanduser('~/Library/Android/sdk/platform-tools/adb')
s=sys.argv[1]
assert s in ('emulator-5572','emulator-5574')
def run(*a):
 p=subprocess.run([adb,'-s',s,*a],capture_output=True,text=True,timeout=45)
 if p.returncode: raise RuntimeError(p.stderr)
 return p.stdout
assert {'emulator-5572':'pla291-a35','emulator-5574':'pla291-a36'}[s] in run('emu','avd','name')
def content(*a):
 out=run('shell','su','0','content',*a)
 if 'Error' in out or 'Exception' in out: raise RuntimeError(out)
 return out
def insert(uri,**values):
 args=['insert','--uri',uri]
 for k,v in values.items(): args+=['--bind',f'{k}:{"i" if isinstance(v,int) else "s"}:{v}']
 return content(*args)
def evidence(label,n):
 out=run('shell','am','instrument','-w','-e','expected',str(n),'org.plainphone.test/org.plainphone.counts.CombinedCountInstrumentation')
 print(label+'\n'+out,flush=True)
 rows.append({'step':label,'output':out})
 pathlib.Path(os.environ['PAPERCLIP_RUN_SCRATCH_DIR'],s+'-provider-gate.json').write_text(json.dumps(rows,indent=2))
 if 'outcome=PASS' not in out: raise SystemExit('STOP: required provider gate failed')
rows=[]
run('shell','am','force-stop','org.plainphone')
run('shell','am','force-stop','com.google.android.apps.messaging')
content('update','--uri','content://sms','--bind','read:i:1')
content('update','--uri','content://mms','--bind','read:i:1')
# AVD has synthetic records only. High IDs isolate this run's fixtures.
for uri in ['content://sms','content://mms']:
 content('delete','--uri',uri,'--where','"_id >= 800000"')
# Provider supplies real thread IDs; no recipient is read back or logged.
threads=[]
a=os.environ['COUNT_FIXTURE_RECIPIENT_A'];b=os.environ['COUNT_FIXTURE_RECIPIENT_B']
assert all(re.fullmatch(r'55501[0-9]{2}', n) for n in (a,b)), 'Synthetic fixture recipients only'
for recipients in [a,b,a+'&recipient='+b]:
 out=run('shell',"su 0 content query --uri 'content://mms-sms/threadID?recipient="+recipients+"' --projection _id")
 threads.append(int(re.search(r'_id=(\d+)',out).group(1)))
insert('content://sms/inbox',_id=800001,thread_id=threads[0],read=0)
insert('content://sms/inbox',_id=800002,thread_id=threads[1],read=0)
insert('content://mms/inbox',_id=800001,thread_id=threads[2],read=0,m_type=132,v=18)
evidence('two_sms_conversations_plus_group_pdu_and_id_collision',3)
insert('content://sms/sent',_id=800003,thread_id=threads[0],read=0)
insert('content://mms/sent',_id=800003,thread_id=threads[2],read=0,m_type=128,v=18)
insert('content://sms/draft',_id=800004,thread_id=threads[0],read=0)
insert('content://mms/drafts',_id=800004,thread_id=threads[2],read=0,m_type=128,v=18)
evidence('sent_sms_sent_mms_sms_draft_mms_draft_excluded',3)
content('update','--uri','content://sms/800002','--bind','read:i:1')
evidence('same_id_sms_and_mms_count_as_two',2)
