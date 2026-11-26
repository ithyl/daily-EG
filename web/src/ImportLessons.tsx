import {useState} from 'react';
import {api,type Plan} from './api';

type Preview={lessons:Array<{date:string;topic:string;level:string;content:{title:string;segments:Array<{en:string;zh:string}>;[key:string]:unknown}}>;warnings:string[]};
const example={date:new Date().toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'}),topic:'原课程主题',level:'B1',content:{title:'Replace with the original title',segments:[{en:'Paste the exact original English paragraph here.',zh:'在此粘贴对应的原始中文译文。'}],words:[],expressions:[],pronunciationTips:[],exercises:[],studySteps:[]}};
export function ImportLessons({plans,demo,onImported}:{plans:Plan[];demo:boolean;onImported:()=>Promise<void>}){
 const [source,setSource]=useState('');const [preview,setPreview]=useState<Preview|null>(null);
 const [plan,setPlan]=useState('');const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [result,setResult]=useState('');
 const change=(text:string)=>{setSource(text);setPreview(null);setResult('');setError('')};
 async function parse(){setBusy(true);setError('');setPreview(null);setResult('');try{setPreview(await api<Preview>('/lessons/import/preview','POST',{source}))}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 async function confirm(){setBusy(true);setError('');try{const response=await api<{results:Array<{date:string;status:string}>}>('/lessons/import','POST',{planId:Number(plan||plans[0]?.id),source});setResult(response.results.map(r=>`${r.date}：${r.status==='IMPORTED'?'导入成功':'已有课程，已跳过'}`).join('\n'));setPreview(null);await onImported()}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 return <div className="settings-card"><h2>导入今日课程 / 历史课程</h2>
 <p>粘贴原任务的规范 JSON 或完整 JSON Markdown 代码块。历史课程支持 JSON 数组或 {'{lessons: [...]}'}，每批最多 100 篇。</p>
 <p>自由格式 Markdown/文本无法可靠匹配双语和练习时会报错，不会重写原文。已有日期一律跳过，包括草稿。</p>
 {demo&&<p className="error">请退出体验模式并登录后导入。</p>}
 <label>学习计划 <select value={plan||String(plans[0]?.id||'')} disabled={busy||demo} onChange={e=>{setPlan(e.target.value);setPreview(null)}}>{plans.map(p=><option key={p.id} value={p.id}>{p.title}</option>)}</select></label>
 <div className="action-row"><label>读取 JSON / Markdown / 文本文件 <input type="file" accept=".json,.md,.txt" disabled={busy} onChange={async e=>{const f=e.target.files?.[0];if(!f)return;if(f.size>2_000_000){setError('文件超过 2 MB');return}try{change(await f.text())}catch{setError('读取文件失败')}}}/></label><button className="btn outline" disabled={busy} onClick={()=>{const url=URL.createObjectURL(new Blob([JSON.stringify(example,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='lesson-template.json';a.click();URL.revokeObjectURL(url)}}>下载格式模板</button></div>
 <textarea aria-label="每日课程原文或JSON" style={{width:'100%',minHeight:260,whiteSpace:'pre-wrap'}} disabled={busy} value={source} onChange={e=>change(e.target.value)} placeholder="粘贴课程 JSON；不确定格式时先下载模板。"/>
 <button className="btn primary" disabled={busy||demo||!source.trim()||!plans.length} onClick={parse}>{busy?'处理中…':'解析并预览（不写入）'}</button>
 {error&&<p role="alert" className="error">{error}</p>}
 {preview&&<div><h3>预览：{preview.lessons.length} 篇</h3>{preview.warnings.map(w=><p key={w}>{w}</p>)}{preview.lessons.map(l=><details key={l.date} open={preview.lessons.length===1}><summary>{l.date} · {l.content.title} · {l.level}</summary>{l.content.segments.map((s,i)=><div key={i}><p>{s.en}</p><p>{s.zh}</p></div>)}<h4>词汇、IPA、表达、发音、问题及原始步骤</h4><pre style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{JSON.stringify(Object.fromEntries(Object.entries(l.content).filter(([key])=>!['segments','title'].includes(key))),null,2)}</pre></details>)}<button className="btn primary" disabled={busy} onClick={confirm}>确认按原日期导入 {preview.lessons.length} 篇</button></div>}
 {result&&<pre role="status" style={{whiteSpace:'pre-wrap'}}>{result}</pre>}
 </div>;
}
