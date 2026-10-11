# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Owned-AVD-only synthetic provider fixtures; counts-only output. Never ships."""
import os,subprocess,re,json,pathlib,sys
adb=os.path.expanduser('~/Library/Android/sdk/platform-tools/adb')
s=sys.argv[1]
assert s in ('emulator-5572','emulator-5574')
def run(*a):
 p=subprocess.run([adb,'-s',s,*a],capture_output=True,text=True,timeout=45)
 if p.returncode or "Exception" in p.stderr or "Error" in p.stderr:
  raise RuntimeError("adb fixture command failed; no provider data logged")
 return p.stdout
assert 'RUNNING_UNLOCKED' in run('shell','am','get-started-user-state','0'), 'Unlock the owned test AVD before fixtures'
assert {'emulator-5572':'pla291-a35','emulator-5574':'pla291-a36'}[s] in run('emu','avd','name')
def content(*a):
 out=run('shell','su','0','content',*a)
 if 'Error' in out or 'Exception' in out: raise RuntimeError('Provider fixture command failed; no provider data logged')
 return out
def insert(uri,**values):
 args=['insert','--uri',uri]
 for k,v in values.items(): args+=['--bind',f'{k}:{"l" if isinstance(v,int) else "s"}:{v}']
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
assert a != b, 'Use distinct synthetic recipients'
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
# Explicit metadata readback verifies insertion was not silently normalized.
database='/data/user/0/com.android.providers.telephony/databases/mmssms.db'
def metadata_sql(sql):
 # Owned AVD fixture verification only: IDs and metadata predicates, never content/address columns.
 return run('shell','su','0','sqlite3',database,'"'+sql+'"').strip()

def verify(uri, expected):
 table='pdu' if uri.startswith('content://mms/') else 'sms'
 predicates=[f'_id = {int(uri.rsplit("/",1)[1])}']
 predicates += [f'{key} = {int(value)}' for key,value in expected.items()]
 assert metadata_sql('SELECT count(*) FROM '+table+' WHERE '+' AND '.join(predicates))=='1', 'Fixture metadata mismatch'

def ids(uri):
 table='pdu' if uri=='content://mms' else 'sms'
 return set(map(int, metadata_sql('SELECT _id FROM '+table).split()))

created=[]
def mms(box='inbox', **values):
 before=ids('content://mms')
 insert('content://mms/'+box,thread_id=threads[2],read=0,v=18,**values)
 added=ids('content://mms')-before
 assert len(added)==1, 'Expected exactly one new synthetic MMS'
 ident=added.pop()
 created.append('content://mms/'+str(ident))
 return ident

def sms(ident, box='inbox', thread=None, **values):
 assert ident not in ids('content://sms'), 'Do not overwrite an existing SMS'
 insert('content://sms/'+box,_id=ident,thread_id=threads[0] if thread is None else thread,read=0,**values)
 created.append('content://sms/'+str(ident))
 return ident

try:
 # MmsProvider discards caller _id. Use its allocated ID in the paired SMS.
 collision=mms(sub_id=included,m_type=132,date=1791670000,ct_t='application/vnd.wap.multipart.related')
 while collision in ids('content://sms'):
  assert len(created) < 100, 'Could not allocate a collision-free SMS fixture ID'
  content('update','--uri','content://mms/'+str(collision),'--bind','read:i:1')
  collision=mms(sub_id=included,m_type=132,date=1791670000,ct_t='application/vnd.wap.multipart.related')
 sms(collision,sub_id=included,date=1791670000000)
 for recipient,kind in [(a,137),(b,151)]:
  insert(f'content://mms/{collision}/addr',address=recipient,type=kind,charset=106)
 insert(f'content://mms/{collision}/part',ct='text/plain',text='TEST',chset=106)
 verify(f'content://sms/{collision}',{'_id':collision,'date':1791670000000,'sub_id':included,'read':0})
 verify(f'content://mms/{collision}',{'_id':collision,'date':1791670000,'sub_id':included,'read':0})
 evidence('included_subscription_equal_id_equal_normalized_date',2,str(collision),1,1)
 restoredSms=sms(800002,thread=threads[1],sub_id=-1)
 restoredMms=mms(sub_id=-1,m_type=132)
 verify(f'content://sms/{restoredSms}',{'sub_id':-1,'read':0})
 verify(f'content://mms/{restoredMms}',{'sub_id':-1,'read':0})
 evidence('restored_subscription_minus_one',4,f'{restoredSms},{restoredMms}',1,1)
 # Missing addresses cannot match SmsProvider's emergency-number OR exception.
 # These builds reject inserts for an unassociated subscription. Insert synthetic
 # rows on the included subscription, then move only their sub_id via the fixture writer.
 excludedSms=sms(800005,sub_id=included)
 excludedMms=mms(sub_id=included,m_type=132)
 content('update','--uri',f'content://sms/{excludedSms}','--bind',f'sub_id:i:{excluded}')
 content('update','--uri',f'content://mms/{excludedMms}','--bind',f'sub_id:i:{excluded}')
 verify(f'content://sms/{excludedSms}',{'sub_id':excluded,'read':0})
 verify(f'content://mms/{excludedMms}',{'sub_id':excluded,'read':0})
 evidence('excluded_subscription_rows_exist_but_neither_query_counts_them',4,f'{excludedSms},{excludedMms}',0,0)
 notification=mms(sub_id=included,m_type=130)
 verify(f'content://mms/{notification}',{'m_type':130,'read':0,'sub_id':included})
 evidence('unread_inbox_download_notification_hidden_from_non_default_caller',4,str(notification),0,0)
 nonInbox=[]
 for box,ident in [('sent',800003),('draft',800004),('outbox',800007)]:
  nonInbox.append(sms(ident,box,sub_id=included))
  nonInbox.append(mms('drafts' if box=='draft' else box,sub_id=included,m_type=128))
 evidence('sent_draft_outbox_excluded',4,','.join(map(str,nonInbox)),0,0)
finally:
 for uri in reversed(created): content('delete','--uri',uri)
