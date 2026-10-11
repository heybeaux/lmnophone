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
def evidence(label,n,ids,sms,mms):
 out=run('shell','am','instrument','-w','-e','expected',str(n),'-e','fixtureIds',ids,'-e','expectedSms',str(sms),'-e','expectedMms',str(mms),'org.plainphone.test/org.plainphone.counts.CombinedCountInstrumentation')
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
# Query subscription IDs only, never SIM identifiers or account/contact data.
sub_rows=content('query','--uri','content://telephony/siminfo','--projection','_id')
subs=[int(x) for x in re.findall(r'_id=(\d+)',sub_rows)]
assert subs, 'No included subscription on this AVD'
included=subs[0]
excluded=max(subs)+900000
# Explicit sub_id and date readback verifies insertion was not silently normalized.
def verify(uri, expected):
 out=content('query','--uri',uri,'--projection',':'.join(expected))
 for key,value in expected.items():
  assert re.search(r'(?<!\w)'+re.escape(key)+'='+str(value)+r'(?:,|$)',out.strip()), 'Fixture metadata mismatch: '+key

insert('content://sms/inbox',_id=800001,thread_id=threads[0],read=0,sub_id=included,date=1791670000000)
insert('content://mms/inbox',_id=800001,thread_id=threads[2],read=0,sub_id=included,m_type=132,v=18,
       date=1791670000,ct_t='application/vnd.wap.multipart.related')
for recipient,kind in [(a,137),(b,151)]:
 insert('content://mms/800001/addr',address=recipient,type=kind,charset=106)
insert('content://mms/800001/part',ct='text/plain',text='TEST',chset=106)
verify('content://sms/800001',{'_id':800001,'date':1791670000000,'sub_id':included,'read':0})
verify('content://mms/800001',{'_id':800001,'date':1791670000,'sub_id':included,'read':0})
evidence('included_subscription_equal_id_equal_normalized_date',2,'800001',1,1)
insert('content://sms/inbox',_id=800002,thread_id=threads[1],read=0,sub_id=-1)
insert('content://mms/inbox',_id=800002,thread_id=threads[2],read=0,sub_id=-1,m_type=132,v=18)
verify('content://sms/800002',{'sub_id':-1,'read':0})
verify('content://mms/800002',{'sub_id':-1,'read':0})
evidence('restored_subscription_minus_one',4,'800002',1,1)
# Missing addresses cannot match SmsProvider's emergency-number OR exception.
insert('content://sms/inbox',_id=800005,thread_id=threads[0],read=0,sub_id=excluded)
insert('content://mms/inbox',_id=800005,thread_id=threads[2],read=0,sub_id=excluded,m_type=132,v=18)
verify('content://sms/800005',{'sub_id':excluded,'read':0})
verify('content://mms/800005',{'sub_id':excluded,'read':0})
evidence('excluded_subscription_rows_exist_but_neither_query_counts_them',4,'800005',0,0)
insert('content://mms/inbox',_id=800006,thread_id=threads[2],read=0,sub_id=included,m_type=130,v=18)
verify('content://mms/800006',{'m_type':130,'read':0,'sub_id':included})
evidence('unread_inbox_download_notification_hidden_from_non_default_caller',4,'800006',0,0)
for uri,kind in [('sent',800003),('draft',800004),('outbox',800007)]:
 insert('content://sms/'+uri,_id=kind,thread_id=threads[0],read=0,sub_id=included)
 insert('content://mms/'+('drafts' if uri=='draft' else uri),_id=kind,thread_id=threads[2],read=0,sub_id=included,m_type=128,v=18)
evidence('sent_draft_outbox_excluded',4,'800003,800004,800007',0,0)
