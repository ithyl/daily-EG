export type Word = {id:number;word:string;ipa:string;meaning:string};
export type Segment = {id:number;en:string;zh:string};
export type Expression = {id:number;text:string;meaning:string;example:string};
export type Exercise = {id:number;question:string;questionZh:string;type:string;reference?:string};
export type PronunciationTip = {id:number;target:string;ipa:string;explanation:string;example:string};
export type Lesson = {id:number;day:number;date:string;title:string;topic:string;level:string;segments:Segment[];words:Word[];expressions:Expression[];exercises:Exercise[];tips?:PronunciationTip[];studySteps?:string[];steps:Record<string,boolean>};
export type LessonSummary = Pick<Lesson,'id'|'day'|'date'|'title'|'topic'|'level'>;
export type Feedback = {mode:string;commentZh:string;improvedAnswerEn:string;issues:string[];retryPromptZh:string};
let csrfToken='';
async function ensureCsrf(){
 if(csrfToken)return;
 const r=await fetch('/api/v1/auth/csrf',{credentials:'include'});
 if(!r.ok) throw new Error('无法连接后端');
 csrfToken=(await r.json()).token;
}
export async function api<T>(path:string,method='GET',data?:unknown):Promise<T>{
 if(method!=='GET')await ensureCsrf();
 const r=await fetch('/api/v1'+path,{method,credentials:'include',headers:{'Content-Type':'application/json',...(method!=='GET'?{'X-XSRF-TOKEN':csrfToken}:{})},body:data===undefined?undefined:JSON.stringify(data)});
 if(!r.ok){const m=await r.json().catch(()=>({message:`HTTP ${r.status}`}));throw new Error(m.message||`HTTP ${r.status}`)}
 if(r.status===204)return undefined as T;
 return r.json();
}
export function resetCsrf(){csrfToken=''}
export async function loadDemo():Promise<Lesson[]>{
 const r=await fetch('/lessons.json');if(!r.ok)throw new Error('无法加载示例文章');
 const ls=await r.json() as Array<Omit<Lesson,'id'|'steps'|'words'|'segments'|'expressions'|'exercises'|'title'> & {title:string;segments:Array<Omit<Segment,'id'>>;words:Array<Omit<Word,'id'>>;expressions:Array<Omit<Expression,'id'>>;exercises:Array<Omit<Exercise,'id'>>}>;
 return ls.map((l,i)=>({...l,id:i+1,words:l.words.map((w,j)=>({...w,id:(i+1)*1000+j})),segments:l.segments.map((s,j)=>({...s,id:(i+1)*1000+j})),expressions:l.expressions.map((e,j)=>({...e,id:(i+1)*1000+j})),exercises:l.exercises.map((e,j)=>({...e,id:(i+1)*1000+j})),steps:JSON.parse(localStorage.getItem(`sf-demo-steps-${i+1}`)||'{}')})).reverse();
}

export type Features={aiEnabled:boolean;ttsEnabled:boolean;asrEnabled:boolean;audioPrivacy:string};
export type Plan={id:number;title:string;timezone:string;level:string;publishTime:string;generationEnabled:boolean};
export type GenerationJob={id:number;planId:number;date:string;status:string;message:string};
export type AudioFeedback=Feedback&{transcript:string;inputType:'AUDIO'};
export async function apiForm<T>(path:string,form:FormData):Promise<T>{
 await ensureCsrf();
 const r=await fetch('/api/v1'+path,{method:'POST',credentials:'include',headers:{'X-XSRF-TOKEN':csrfToken},body:form});
 if(!r.ok){const m=await r.json().catch(()=>({message:`HTTP ${r.status}`}));throw new Error(m.message||`HTTP ${r.status}`)}
 return r.json();
}
export async function fetchLessonAudio(lessonId:number,segmentId?:number):Promise<string>{
 const path=`/api/v1/lessons/${lessonId}/audio`+(segmentId===undefined?'':`?segmentId=${segmentId}`);
 const r=await fetch(path,{credentials:'include'});
 if(!r.ok)throw new Error('音频暂时不可用');
 const b=await r.blob();return URL.createObjectURL(b);
}
