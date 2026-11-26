import React, {useCallback,useEffect,useRef,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {type LucideIcon,LayoutDashboard,BookOpen,Library,ChartColumn,Settings2,ChevronRight,ArrowLeft,Play,Volume2,Mic,Square,CheckCircle2, Circle,BookmarkPlus, ArrowRight,RefreshCcw,LogOut, Headphones, Languages, Sparkles, Search, Menu, X, CircleHelp, AudioLines} from 'lucide-react';
import {api,apiForm,fetchLessonAudio,loadDemo,resetCsrf,type Lesson,type LessonSummary,type Feedback,type AudioFeedback,type Features,type Plan,type GenerationJob,type Word} from './api';
import {useAudioRecorder} from './recorder';
import './style.css';
import {ImportLessons} from './ImportLessons';

type Section='dashboard'|'lessons'|'learn'|'reviews'|'stats'|'settings'|'import';
type StudyTab='read'|'vocab'|'shadow'|'replace'|'speak';
const steps:{key:StudyTab;name:string;sub:string}[]=[
 {key:'read',name:'阅读理解',sub:'双语对照'},{key:'vocab',name:'词汇表达',sub:'发音与句型'},
 {key:'shadow',name:'听力跟读',sub:'逐段练习'},{key:'replace',name:'句型替换',sub:'主动组织'},{key:'speak',name:'自由表达',sub:'情景问答'}
];
const nav:[Section,string,LucideIcon][]=[
 ['dashboard','今日学习',LayoutDashboard],['lessons','课程归档',BookOpen],['reviews','表达复习',Library],['stats','学习统计',ChartColumn],['settings','设置',Settings2],['import','导入今日课程',BookOpen]
];
let currentAudio:HTMLAudioElement|null=null;
let currentAudioUrl:string|null=null;
function stopCurrentAudio(){
 if(currentAudio){currentAudio.pause();currentAudio=null;}
 if(currentAudioUrl){URL.revokeObjectURL(currentAudioUrl);currentAudioUrl=null;}
}
function speak(text:string,rate:number, onEnd?:()=>void){
 stopCurrentAudio();
 if(!('speechSynthesis' in window))return alert('当前浏览器不支持语音朗读');
 window.speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(text);u.lang='en-US';u.rate=rate;u.pitch=1;
 const en=window.speechSynthesis.getVoices().find(v=>v.lang==='en-US');if(en)u.voice=en;
 if(onEnd)u.onend=onEnd;window.speechSynthesis.speak(u);
}
function useSpeechInput(onText:(text:string)=>void){
 const [recording,setRecording]=useState(false);const [note,setNote]=useState('');
 const recognition=useRef<{stop:()=>void}|null>(null);
 const start=useCallback(()=>{
  const W=window as unknown as {SpeechRecognition?:new()=>any;webkitSpeechRecognition?:new()=>any};
  const Klass=W.SpeechRecognition||W.webkitSpeechRecognition;
  if(!Klass){setNote('当前浏览器不支持语音转写，请使用 Chrome 或直接输入英文。');return;}
  const engine=new Klass();engine.lang='en-US';engine.continuous=false;engine.interimResults=false;
  engine.onresult=(event:any)=>{onText(event.results[0][0].transcript);setNote('识别完成，请核对文本后提交。');};
  engine.onerror=(e:any)=>{setNote('语音识别失败：'+String(e.error||'unknown'));setRecording(false)};
  engine.onend=()=>{setRecording(false)};
  recognition.current=engine;setNote('正在识别英语，请开始说话…');setRecording(true);
  try{engine.start()}catch{setRecording(false);setNote('无法启动录音，请检查麦克风权限。')}
 },[onText]);
 const stop=()=>{recognition.current?.stop();setRecording(false)};
 return {recording,note,start,stop};
}
function App(){
 const [user,setUser]=useState('');const [loginName,setLoginName]=useState('admin');const [loginPass,setLoginPass]=useState('');
 const [demo,setDemo]=useState(false);const [demoLessons,setDemoLessons]=useState<Lesson[]>([]);
 const [lessons,setLessons]=useState<LessonSummary[]>([]);const [lesson,setLesson]=useState<Lesson|null>(null);
 const [section,setSection]=useState<Section>('dashboard');const [studyTab,setStudyTab]=useState<StudyTab>('read');
 const [error,setError]=useState('');const [busy,setBusy]=useState(false);const [menu,setMenu]=useState(false);
 const [translation,setTranslation]=useState<'inline'|'hidden'>('inline');const [rate,setRate]=useState(.9);
 const [selectedWord,setSelectedWord]=useState<Word|null>(null);const [saved,setSaved]=useState<number[]>([]);
 const [answer,setAnswer]=useState('');const [activeQuestion,setActiveQuestion]=useState(0);const [feedback,setFeedback]=useState<Feedback|null>(null);
 const [reveal,setReveal]=useState(false);const [reviewCards,setReviewCards]=useState<Array<{id:number;word:string;ipa:string;meaning:string}>>([]);
 const [stats,setStats]=useState<{availableLessons:number;completedLessons:number;attempts:number;savedWords:number}|null>(null);
 const [search,setSearch]=useState('');
 const speech=useSpeechInput(setAnswer);
 const recorder=useAudioRecorder();
 const [features,setFeatures]=useState<Features>({aiEnabled:false,ttsEnabled:false,asrEnabled:false,audioPrivacy:''});
 const [plans,setPlans]=useState<Plan[]>([]);
 const [jobs,setJobs]=useState<GenerationJob[]>([]);
 const [generateDate,setGenerateDate]=useState(new Date().toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'}));
 const [generationMessage,setGenerationMessage]=useState('');
 const [cloudTts,setCloudTts]=useState(false);
 useEffect(()=>{api<{username:string}>('/auth/me').then(u=>setUser(u.username)).catch(()=>{})},[]);
 const refresh=useCallback(async()=>{
  if(demo)return;
  const [list,stat]=await Promise.all([api<LessonSummary[]>('/lessons'),api<typeof stats>('/statistics')]);
  setLessons(list);setStats(stat);if(list.length){
   const current=await api<Lesson>('/lessons/'+list[0].id);
   setLesson(prev=>prev??current);
  }
 },[demo]);
 useEffect(()=>{if(user&&!demo){
  refresh().catch(e=>setError(e.message));
  api<Features>('/features').then(setFeatures).catch(()=>{});
  api<Plan[]>('/plans').then(setPlans).catch(()=>{});
 }},[user,demo,refresh]); // initial login
 useEffect(()=>{
  if(demo||!lessons.length)return;
  // Select the next date with no course, avoiding immediate conflicts with imported lessons.
  if(lessons.some(l=>l.date===generateDate)){
   const next=new Date(generateDate+'T12:00:00Z');next.setUTCDate(next.getUTCDate()+1);
   setGenerateDate(next.toISOString().slice(0,10));
  }
 },[demo,lessons,generateDate]);
 async function login(e:React.FormEvent){e.preventDefault();setBusy(true);setError('');try{
  const u=await api<{username:string}>('/auth/login','POST',{username:loginName,password:loginPass});
  setUser(u.username);resetCsrf();
 }catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 async function startDemo(){setBusy(true);setError('');try{
  const data=await loadDemo();setDemoLessons(data);setLessons(data);setLesson(data[0]);setStats({availableLessons:data.length,completedLessons:0,attempts:0,savedWords:0});setDemo(true);setUser('体验用户');
 }catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 async function choose(id:number){setError('');setBusy(true);try{
  const next=demo?demoLessons.find(l=>l.id===id):await api<Lesson>('/lessons/'+id);
  if(!next)throw new Error('课程不存在');setLesson(next);setStudyTab('read');setSection('learn');setAnswer('');setFeedback(null);setActiveQuestion(0);recorder.clear();setSelectedWord(null);setMenu(false);
 }catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 async function markStep(step:StudyTab){if(!lesson)return;try{
  const next=!lesson.steps[step];const updated={...lesson,steps:{...lesson.steps,[step]:next}};

  if(demo){localStorage.setItem(`sf-demo-steps-${lesson.id}`,JSON.stringify(updated.steps));setDemoLessons(xs=>xs.map(l=>l.id===lesson.id?updated:l))}
  else await api(`/lessons/${lesson.id}/steps/${step}`,'PUT',{completed:next});
  setLesson(current=>current?.id===updated.id?updated:current);
 }catch(e){setError((e as Error).message)}}
 async function saveWord(w:Word){try{
  if(demo){const list=JSON.parse(localStorage.getItem('sf-demo-words')||'[]') as number[];if(!list.includes(w.id))list.push(w.id);localStorage.setItem('sf-demo-words',JSON.stringify(list));}
  else await api(`/words/${w.id}/save`,'POST');
  setSaved(xs=>[...new Set([...xs,w.id])]);
 }catch(e){setError((e as Error).message)}}
 async function submitAnswer(){const e=lesson?.exercises[activeQuestion];if(!e||!answer.trim())return;
  setBusy(true);setFeedback(null);setError('');try{
   if(demo){setFeedback({mode:'local',commentZh:'体验模式已记录你的练习。完整版本连接后端后可保存练习结果并启用AI评价。',improvedAnswerEn:e.reference||'',issues:[],retryPromptZh:'请对照参考表达，尝试不看答案再说一次。'});localStorage.setItem('sf-demo-last-answer',answer)}
   else setFeedback(await api<Feedback>(`/exercises/${e.id}/attempts`,'POST',{text:answer}));
  }catch(err){setError((err as Error).message)}finally{setBusy(false)}}
 async function play(text:string,segmentId?:number){
  if(!lesson)return;
  if(!demo&&cloudTts&&features.ttsEnabled){
   try{
    window.speechSynthesis?.cancel();stopCurrentAudio();
    const url=await fetchLessonAudio(lesson.id,segmentId);
    const player=new Audio(url);currentAudio=player;currentAudioUrl=url;player.playbackRate=rate;
    player.onended=()=>{if(currentAudio===player)stopCurrentAudio()};
    player.onerror=()=>{if(currentAudio===player)stopCurrentAudio()};
    await player.play();return;
   }catch{setError('云端音频不可用，已改用浏览器朗读。');}
  }
  speak(text,rate);
 }
 async function submitRecorded(){
  const e=lesson?.exercises[activeQuestion];if(!e||!recorder.audioBlob||demo)return;
  setBusy(true);setError('');setFeedback(null);
  try{
   const ext=recorder.audioBlob.type==='audio/mp4'?'mp4':recorder.audioBlob.type==='audio/ogg'?'ogg':'webm';
   const fd=new FormData();fd.append('file',recorder.audioBlob,`answer.${ext}`);
   const result=await apiForm<AudioFeedback>(`/exercises/${e.id}/audio-attempts`,fd);
   setAnswer(result.transcript);setFeedback(result);recorder.clear();
  }catch(e){setError((e as Error).message)}finally{setBusy(false)}
 }
 async function generateLesson(){
  if(demo||!plans.length)return;
  setBusy(true);setError('');setGenerationMessage('正在生成课程，可能需要约30–90秒…');
  try{
   const result=await api<{lessonId:number;status:string;day:number}>(
    '/admin/generate-lesson','POST',{planId:plans[0].id,date:generateDate});
   setGenerationMessage(`Day ${result.day} 已生成，状态：${result.status==='PUBLISHED'?'已发布':'待发布'}`);
   setJobs(await api<GenerationJob[]>('/admin/generation-jobs'));
   await refresh();
   if(result.status==='PUBLISHED')await choose(result.lessonId);
  }catch(e){setError((e as Error).message);setGenerationMessage('生成未成功，请检查 AI 配置或任务日志。');
    try{setJobs(await api<GenerationJob[]>('/admin/generation-jobs'))}catch{}
  }finally{setBusy(false)}
 }
 async function viewJobs(){if(demo)return;try{setJobs(await api<GenerationJob[]>('/admin/generation-jobs'))}catch(e){setError((e as Error).message)}}
 async function openReviews(){setSection('reviews');setMenu(false);if(demo){const ids=JSON.parse(localStorage.getItem('sf-demo-words')||'[]') as number[];
  setReviewCards(demoLessons.flatMap(l=>l.words).filter(w=>ids.includes(w.id)).map(w=>({...w})))
 }else try{setReviewCards(await api('/reviews/due'))}catch(e){setError((e as Error).message)}}
 async function reviewGrade(id:number,remembered:boolean){if(demo){setReviewCards(xs=>xs.filter(x=>x.id!==id));return}
  try{await api(`/reviews/${id}/grade`,'POST',{remembered});setReviewCards(xs=>xs.filter(x=>x.id!==id))}catch(e){setError((e as Error).message)}}
 async function logout(){if(!demo){try{await api('/auth/logout','POST')}catch{}}setUser('');setDemo(false);setLessons([]);setLesson(null);resetCsrf()}
 if(!user)return <div className="login-screen"><div className="login-card">
  <div className="brand large"><span className="brand-mark"><AudioLines size={24}/></span><span>SpeakFlow</span></div>
  <h1>每天15分钟，<br/>把英语真正说出来。</h1><p className="muted">阅读 · 跟读 · 句型替换 · 主动表达</p>
  <form onSubmit={login} className="login-form"><label>用户名<input value={loginName} onChange={e=>setLoginName(e.target.value)} autoComplete="username"/></label>
  <label>密码<input type="password" value={loginPass} onChange={e=>setLoginPass(e.target.value)} autoComplete="current-password" placeholder="由环境变量设置"/></label>
  <button disabled={busy} className="btn primary">{busy?'登录中…':'登录学习'} <ArrowRight size={17}/></button></form>
  <div className="or-line">或者</div><button onClick={startDemo} disabled={busy} className="btn outline full">无需数据库 · 进入体验模式</button>
  {error&&<p className="error">{error}</p>}
  <p className="tiny">体验模式使用真实的8篇历史文章，学习记录仅保存在当前浏览器。</p>
 </div><div className="login-aside"><span className="top-note">YOUR DAILY ENGLISH SPACE</span><div className="login-quote">“The best way to learn to speak is to start speaking.”</div><p>Understand → Practice → Express</p><div className="decor-circle"/></div></div>;
 const completed=lesson?steps.filter(s=>lesson.steps?.[s.key]).length:0;
 return <div className="shell">
  {menu&&<div className="mobile-shade" onClick={()=>setMenu(false)}/>}
  <aside className={'sidebar '+(menu?'mobile-open':'')}><div className="brand"><span className="brand-mark"><AudioLines size={22}/></span><span>SpeakFlow</span></div>
   <div className="sidebar-group-title">WORKSPACE</div>
   <nav className="nav">
    {nav.map(([key,label,Icon])=><button key={key} className={'nav-link '+(section===key?'active':'')} onClick={()=>{setMenu(false);if(key==='reviews')void openReviews();else setSection(key)}}><Icon size={18}/>{label}</button>)}
   </nav>
   <div className="sidebar-fill"/><div className="sidebar-card"><span className="pill">YOUR JOURNEY</span><div className="sidebar-card-title">Small steps,<br/>stronger English.</div><div className="sidebar-card-meta">每天进步一点点</div></div>
   <button className="side-account" onClick={logout}><span className="avatar">{user.slice(0,1).toUpperCase()}</span><span className="account-name">{user}<small>{demo?'浏览器体验模式':'个人学习空间'}</small></span><LogOut size={16}/></button>
  </aside>
  <div className="main-wrap"><header className="topbar"><button className="hamburger" onClick={()=>setMenu(true)}><Menu size={22}/></button><div className="breadcrumb">学习空间 <ChevronRight size={14}/> <b>{section==='learn'?'课程学习':nav.find(x=>x[0]===section)?.[1]}</b></div><div className="top-right">{demo&&<span className="mode-tag">体验模式 · 未连接 MySQL</span>}<span className="date-label">2026 · ENGLISH EVERY DAY</span><span className="avatar small">{user.slice(0,1).toUpperCase()}</span></div></header>
  <main className="page">
   {error&&<div className="warning"><CircleHelp size={17}/>{error}<button onClick={()=>setError('')}><X size={16}/></button></div>}
   {section==='dashboard'&&<><div className="page-head"><span className="eyebrow">DAILY LEARNING SPACE</span><h1>继续，让英语成为你的表达习惯<span className="accent-dot">.</span></h1><p>每天一篇文章，重点不止是读懂，而是能独立说出来。</p></div>
    <div className="hero"><div className="hero-main"><div className="hero-kicker"><span className="light-dot"/> 最新可用课程 · Day {lesson?.day||'—'}</div><h2>{lesson?.title||'暂无课程'}</h2><p>{lesson?.topic||'英语练习'} · {lesson?.level||'B1+'} · 约 15 分钟</p><button className="btn white" onClick={()=>setSection('learn')}>开始今天的练习 <ArrowRight size={17}/></button></div><div className="hero-symbol"><AudioLines size={96} strokeWidth={1.2}/></div></div>
    <div className="stat-grid"><Stat icon={<BookOpen size={20}/>} label="可学习文章" value={lessons.length} unit="篇"/><Stat icon={<CheckCircle2 size={20}/>} label="当前课程进度" value={completed} unit="/ 5"/><Stat icon={<Mic size={20}/>} label="练习重点" value="开口" unit="表达"/></div>
    <div className="section-heading"><div><h2>最近的课程</h2><p>课文原文、翻译和音标均已整理</p></div><button className="link-button" onClick={()=>setSection('lessons')}>查看全部 <ArrowRight size={16}/></button></div>
    <div className="lesson-grid">{lessons.slice(0,4).map(l=><LessonTile key={l.id} lesson={l} onClick={()=>choose(l.id)}/>)}</div>
    <div className="info-banner"><Sparkles size={21}/><div><b>今天的练习目标</b><p>读完文章后，不看原文，用3–5句话描述一个真实场景。</p></div></div>
   </>}
   {section==='lessons'&&<><div className="page-head"><span className="eyebrow">YOUR LESSON LIBRARY</span><h1>课程归档<span className="accent-dot">.</span></h1><p>按时间回顾英语原文，随时重新练习。</p></div>
     <div className="search-wrap"><Search size={18}/><input placeholder="搜索文章标题或主题" value={search} onChange={e=>setSearch(e.target.value)}/></div>
     <div className="archive-list">{lessons.filter(l=>`${l.title} ${l.topic}`.toLowerCase().includes(search.toLowerCase())).map(l=><button key={l.id} className="archive-row" onClick={()=>choose(l.id)}><div className="day-square">{l.day}</div><div className="archive-detail"><b>{l.title}</b><small>{l.date} · {l.topic} · {l.level}</small></div><ChevronRight size={18}/></button>)}</div>
   </>}
   {section==='learn'&&lesson&&<><button className="back-link" onClick={()=>setSection('lessons')}><ArrowLeft size={16}/> 课程归档</button><div className="lesson-header"><div><span className="eyebrow">DAY {lesson.day} <span className="dot-sep">·</span> {lesson.topic.toUpperCase()} <span className="dot-sep">·</span> {lesson.level}</span><h1>{lesson.title}</h1><p>每天15分钟 · 阅读、跟读、主动表达</p></div><div className="lesson-date">{lesson.date}</div></div>
     <div className="progress-bar"><span style={{width:`${completed/5*100}%`}}/></div><div className="progress-caption">学习进度 {completed}/5</div>
     <div className="study-tabs">{steps.map((s,i)=><button key={s.key} className={'study-tab '+(studyTab===s.key?'selected':'')} onClick={()=>{setStudyTab(s.key);setFeedback(null)}}><span className="step-num">{lesson.steps[s.key]?<CheckCircle2 size={17}/>:String(i+1).padStart(2,'0')}</span><span className="step-title">{s.name}<small>{s.sub}</small></span></button>)}</div>
     <div className="content-layout"><section className="work-panel">
      {studyTab==='read'&&<><div className="panel-heading"><div><span className="eyebrow">01 / READING</span><h2>文章阅读</h2></div><div className="panel-tools"><button className="tool-btn" onClick={()=>setTranslation(t=>t==='inline'?'hidden':'inline')}><Languages size={16}/>{translation==='inline'?'隐藏译文':'显示译文'}</button><button className="tool-btn" onClick={()=>play(lesson.segments.map(s=>s.en).join(' '))}><Play size={16}/> 全文朗读</button></div></div>
       {Boolean(lesson.studySteps?.length)&&<div className="info-banner"><div><b>原课程学习步骤</b>{lesson.studySteps?.map((text,i)=><p key={i}>{i+1}. {text}</p>)}</div></div>}<div className="reading-body">{lesson.segments.map((s,i)=><div className="segment" key={s.id}><button className="segment-audio" title="朗读本段" onClick={()=>play(s.en,s.id)}><Volume2 size={16}/></button><div><p className="en-text">{s.en}</p>{translation==='inline'&&<p className="zh-text">{s.zh}</p>}</div><span className="segment-no">{String(i+1).padStart(2,'0')}</span></div>)}</div>
       <div className="section-footer"><button className="btn outline" onClick={()=>markStep('read')}>{lesson.steps.read?'已读完 ✓':'标记阅读完成'}</button><button className="btn primary" onClick={()=>setStudyTab('vocab')}>下一步：重点表达 <ArrowRight size={17}/></button></div>
      </>}
      {studyTab==='vocab'&&<><PanelHeader step="02 / VOCABULARY" title="词汇与表达" desc="点击单词收听美式发音，收藏后加入复习库。"/>
        <div className="word-grid">{lesson.words.map(w=><button key={w.id} className={'word-card '+(selectedWord?.id===w.id?'chosen':'')} onClick={()=>setSelectedWord(w)}><div><b>{w.word}</b><small>{w.ipa}</small></div><span>{w.meaning}</span></button>)}</div>
        {selectedWord&&<div className="word-detail"><div><span className="eyebrow">WORD DETAILS</span><h3>{selectedWord.word} <span>{selectedWord.ipa}</span></h3><p>{selectedWord.meaning}</p></div><div className="detail-actions"><button className="icon-button" title="发音" onClick={()=>speak(selectedWord.word,rate)}><Volume2 size={18}/></button><button className="icon-button" title="收藏" onClick={()=>saveWord(selectedWord)}>{saved.includes(selectedWord.id)?<CheckCircle2 size={19}/>:<BookmarkPlus size={19}/>}</button></div></div>}
        <h3 className="subheading">5 个核心表达</h3><div className="expressions">{lesson.expressions.map((e,i)=><div className="expression" key={e.id}><span className="expression-index">{String(i+1).padStart(2,'0')}</span><div><b>{e.text}</b><p className="exp-zh">{e.meaning}</p><p className="exp-example">{e.example}</p></div><button className="icon-button" title="朗读例句" onClick={()=>speak(e.example,rate)}><Volume2 size={17}/></button></div>)}</div>
        {Boolean(lesson.tips?.length)&&<><h3 className="subheading">发音与连读提示</h3><div className="expressions">{lesson.tips?.map(t=><div className="expression" key={t.id}><div><b>{t.target}</b><p className="exp-zh">{t.ipa}</p><p>{t.explanation}</p><p className="exp-example">{t.example}</p></div><button className="icon-button" onClick={()=>speak(t.example||t.target,rate)} title="朗读提示"><Volume2 size={17}/></button></div>)}</div></>}
        <div className="section-footer"><button className="btn outline" onClick={()=>markStep('vocab')}>{lesson.steps.vocab?'已掌握 ✓':'标记词汇已学'}</button><button className="btn primary" onClick={()=>setStudyTab('shadow')}>下一步：跟读练习 <ArrowRight size={17}/></button></div>
       </>}
      {studyTab==='shadow'&&<><PanelHeader step="03 / SHADOWING" title="听力跟读" desc="先听标准英文，再逐段跟读；可在设置中启用可缓存云端音频。"/>
        <div className="rate-control"><Headphones size={19}/><b>朗读语速</b><div className="rate-pills">{[.75,.9,1,1.2].map(r=><button className={rate===r?'active':''} key={r} onClick={()=>setRate(r)}>{r}×</button>)}</div><button className="btn outline" onClick={()=>play(lesson.segments.map(s=>s.en).join(' '))}><Play size={16}/> 全文播放</button></div>
        <div className="shadow-list">{lesson.segments.map((s,i)=><div key={s.id} className="shadow-line"><span>{String(i+1).padStart(2,'0')}</span><p>{s.en}</p><button className="icon-button" title="播放本段" onClick={()=>play(s.en,s.id)}><Play size={17}/></button></div>)}</div>
        <div className="section-footer"><button className="btn outline" onClick={()=>markStep('shadow')}>{lesson.steps.shadow?'跟读完成 ✓':'标记跟读完成'}</button><button className="btn primary" onClick={()=>setStudyTab('replace')}>下一步：句型替换 <ArrowRight size={17}/></button></div>
       </>}
      {studyTab==='replace'&&lesson.expressions.length===0&&<p>原课程未提供句型表达。</p>}
      {studyTab==='replace'&&lesson.expressions.length>0&&<><PanelHeader step="04 / ACTIVE RECALL" title="句型替换" desc="先根据中文含义，用自己的话说出英文，再查看英文示例。"/>
        <div className="substitution-card"><span className="eyebrow">EXPRESSION PRACTICE</span><span className="pill">{(activeQuestion%lesson.expressions.length)+1} / {lesson.expressions.length}</span>
         <h2>{lesson.expressions[activeQuestion%lesson.expressions.length].meaning}</h2><p>尽量不用翻译整句，直接调用熟悉的英语表达。</p>
         {reveal&&<div className="answer-preview"><b>{lesson.expressions[activeQuestion%lesson.expressions.length].text}</b><span>{lesson.expressions[activeQuestion%lesson.expressions.length].example}</span></div>}
         <div className="action-row"><button className="btn outline" onClick={()=>setReveal(r=>!r)}>{reveal?'隐藏答案':'查看英文示例'}</button><button className="btn primary" onClick={()=>{setActiveQuestion(i=>(i+1)%lesson.expressions.length);setReveal(false)}}>换一个表达 <RefreshCcw size={16}/></button></div>
        </div><div className="section-footer"><button className="btn outline" onClick={()=>markStep('replace')}>{lesson.steps.replace?'已完成 ✓':'标记练习完成'}</button><button className="btn primary" onClick={()=>{setStudyTab('speak');setActiveQuestion(0)}}>下一步：独立表达 <ArrowRight size={17}/></button></div>
       </>}
      {studyTab==='speak'&&<><PanelHeader step="05 / SPEAKING" title="自由表达" desc="不看原文，先自己说完，再获取反馈。"/>
         <div className="question-tabs">{lesson.exercises.map((_,i)=><button key={i} className={activeQuestion===i?'chosen':''} onClick={()=>{setActiveQuestion(i);setAnswer('');setFeedback(null);recorder.clear()}}>Question {i+1}</button>)}</div>
         <div className="question-card"><span className="eyebrow">SPEAKING CHALLENGE</span><h2>{lesson.exercises[activeQuestion]?.question}</h2><p>{lesson.exercises[activeQuestion]?.questionZh}</p></div>
         <div className="answer-box"><div className="answer-label"><Mic size={17}/> <b>你的回答</b><span>建议30–60秒</span></div><textarea placeholder="用英语自由表达。可点击麦克风使用浏览器语音识别…" value={answer} onChange={e=>setAnswer(e.target.value)}/>
         <div className="mic-controls"><button className={'btn outline '+(speech.recording?'recording':'')} onClick={speech.recording?speech.stop:speech.start}>{speech.recording?<Square size={16}/>:<Mic size={17}/>} {speech.recording?'结束识别':'语音输入'}</button><span>{speech.note||'浏览器识别可能使用浏览器厂商服务，也可以直接输入文字。'}</span></div>
         {!demo&&<div className="recorder-panel"><div className="recorder-title"><AudioLines size={18}/> <b>正式版 · 服务端录音识别</b></div>
          <p>录音将发送至配置的 ASR 服务转写；服务器不长期保存原始音频。请勿录入敏感信息。</p>
          <div className="action-row"><button className={'btn outline '+(recorder.recording?'recording':'')} disabled={!features.asrEnabled||busy} onClick={recorder.recording?recorder.stop:recorder.start}>{recorder.recording?<Square size={16}/>:<Mic size={17}/>} {recorder.recording?'停止录音':'开始录音'}</button>
          <button className="btn primary" disabled={!recorder.audioBlob||busy} onClick={submitRecorded}>提交录音并获取反馈</button></div>
          <p className="tiny">{features.asrEnabled?(recorder.note||'支持 WebM/MP4；仅 HTTPS 或 localhost 能访问麦克风。'):'需要在服务端配置 AI_PROVIDER 和 AI_API_KEY 才能使用录音识别。'}</p>
         </div>}</div>
         <button className="btn primary submit" disabled={busy||!answer.trim()} onClick={submitAnswer}>{busy?'正在处理…':'提交并查看反馈'} <ArrowRight size={17}/></button>
         {feedback&&<div className="feedback">{('transcript' in feedback)&&<div className="answer-preview"><small>语音转写结果（请核对）</small><b>{String((feedback as AudioFeedback).transcript)}</b></div>}<span className="eyebrow">{feedback.mode==='openai'?'AI FEEDBACK':'PRACTICE FEEDBACK'}</span><h3>{feedback.mode==='openai'?'表达反馈':'已收到你的回答'}</h3><p>{feedback.commentZh}</p>{feedback.issues?.map((issue,i)=><p key={i}>• {issue}</p>)}<div className="answer-preview"><small>参考表达</small><b>{feedback.improvedAnswerEn}</b></div><p className="retry-text">{feedback.retryPromptZh}</p><button className="btn outline" onClick={()=>{setAnswer('');setFeedback(null);recorder.clear()}}>再说一次 <RefreshCcw size={16}/></button></div>}
         <div className="section-footer"><button className="btn outline" onClick={()=>markStep('speak')}>{lesson.steps.speak?'已完成 ✓':'标记自主表达完成'}</button></div>
       </>}
      </section>
      <aside className="right-panel"><div className="right-card"><div className="right-card-head"><span>LEARNING PATH</span><span>{completed}/5</span></div><h3>今天的学习步骤</h3><div className="right-progress">{steps.map(s=><button key={s.key} onClick={()=>setStudyTab(s.key)} className={studyTab===s.key?'now':''}>{lesson.steps[s.key]?<CheckCircle2 size={19} className="is-done"/>:<Circle size={19}/>}<span>{s.name}</span></button>)}</div></div>
       <div className="quote-card"><div className="quote-icon"><Sparkles size={19}/></div><p>Don't just understand it.<br/><b>Learn to say it.</b></p><small>每天至少独立说出3句话</small></div>
       <div className="right-card compact"><b>朗读语速</b><div className="rate-pills compact-pills">{[.75,.9,1,1.2].map(r=><button key={r} className={rate===r?'active':''} onClick={()=>setRate(r)}>{r}×</button>)}</div><p className="tiny">默认浏览器朗读，可在设置中切换为可缓存的云端音频。</p></div>
      </aside></div>
   </>}
   {section==='reviews'&&<><div className="page-head"><span className="eyebrow">SPACED REPETITION</span><h1>表达复习<span className="accent-dot">.</span></h1><p>把认识的词汇变成真正能够使用的表达。</p></div>{reviewCards.length? <div className="review-list">{reviewCards.map(w=><div key={w.id} className="review-row"><div><h3>{w.word}</h3><p>{w.ipa} · {w.meaning}</p></div><div className="action-row"><button className="btn outline" onClick={()=>speak(w.word,.85)}><Volume2 size={16}/> 发音</button><button className="btn outline" onClick={()=>reviewGrade(w.id,false)}>再练习</button><button className="btn primary" onClick={()=>reviewGrade(w.id,true)}>记住了</button></div></div>)}</div>:<div className="empty"><Library size={34}/><h2>目前没有待复习的词汇</h2><p>在课程的词汇页中点击收藏，之后就能在这里复习。</p><button className="btn primary" onClick={()=>setSection('learn')}>去学习 <ArrowRight size={16}/></button></div>}</>}
   {section==='stats'&&<><div className="page-head"><span className="eyebrow">LEARNING INSIGHTS</span><h1>学习统计<span className="accent-dot">.</span></h1><p>统计真实的学习行为，而不是给没有依据的英语能力分数。</p></div><div className="stat-grid"><Stat icon={<BookOpen size={20}/>} label="文章总数" value={stats?.availableLessons||lessons.length} unit="篇"/><Stat icon={<CheckCircle2 size={20}/>} label="完整完成" value={stats?.completedLessons||0} unit="篇"/><Stat icon={<Mic size={20}/>} label="口语作答" value={stats?.attempts||0} unit="次"/></div><div className="info-banner"><ChartColumn size={21}/><div><b>持续记录你的输出训练</b><p>课程进度、练习次数和收藏词汇会随着学习积累。体验模式只保存在当前浏览器。</p></div></div></>}
   {section==='import'&&<ImportLessons plans={plans} demo={demo} onImported={refresh}/>} 
   {section==='settings'&&<><div className="page-head"><span className="eyebrow">YOUR PREFERENCES</span><h1>学习设置<span className="accent-dot">.</span></h1><p>调整属于自己的英语学习空间。</p></div><div className="settings-card"><h3>语音偏好</h3><p>朗读使用当前设备的美式英语语音。</p><div className="rate-pills">{[.75,.9,1,1.2].map(r=><button key={r} className={rate===r?'active':''} onClick={()=>setRate(r)}>{r}×</button>)}</div><h3>中文对照</h3><div className="action-row"><button className="btn outline" onClick={()=>setTranslation('inline')}>显示译文 {translation==='inline'?'✓':''}</button><button className="btn outline" onClick={()=>setTranslation('hidden')}>隐藏译文 {translation==='hidden'?'✓':''}</button></div><h3>云端英文朗读</h3><label className="toggle-row"><input type="checkbox" checked={cloudTts} disabled={demo||!features.ttsEnabled} onChange={e=>setCloudTts(e.target.checked)}/> 优先使用可缓存的云端美式音频，失败时回退浏览器朗读</label>
    <p className="tiny">{features.ttsEnabled?'已配置云端 TTS，首次播放可能产生调用费用。':'未配置云端 TTS，继续使用浏览器朗读。'}</p>
    <h3>每日 AI 课程生成</h3>
    <p>根据最近课程自动轮换主题，生成150–220词英文文章、中文译文、IPA词汇与口语练习。</p>
    <div className="generation-box"><label>生成日期<input type="date" value={generateDate} onChange={e=>setGenerateDate(e.target.value)}/></label>
    <button className="btn primary" disabled={demo||!features.aiEnabled||busy||!plans.length} onClick={generateLesson}>{busy?'正在生成…':'手动生成该日课程'} <Sparkles size={16}/></button>
    <button className="btn outline" disabled={demo} onClick={viewJobs}>刷新任务记录</button></div>
    {generationMessage&&<p className="muted">{generationMessage}</p>}
    {!demo&&!features.aiEnabled&&<p className="tiny">AI 未启用：请在服务器环境变量配置 AI_PROVIDER=openai 和 AI_API_KEY。历史课文仍可正常学习。</p>}
    {jobs.length>0&&<div className="job-list">{jobs.slice(0,8).map(j=><div key={j.id}><b>{j.date}</b><span>{j.status}</span><small>{j.message}</small></div>)}</div>}
    <p className="tiny">{demo?'体验模式不连接 MySQL。':'真实模式使用 Spring Boot + MySQL；每日自动任务按学习计划时区运行。'}</p></div></>}
  </main></div>
 </div>
}
function Stat({icon,label,value,unit}:{icon:React.ReactNode;label:string;value:number|string;unit:string}){return <div className="stat-card"><div className="stat-icon">{icon}</div><span>{label}</span><div><b>{value}</b><small>{unit}</small></div></div>}
function LessonTile({lesson,onClick}:{lesson:LessonSummary;onClick:()=>void}){return <button className="lesson-tile" onClick={onClick}><div className="tile-top"><span className="tile-day">DAY {lesson.day}</span><span className="tile-level">{lesson.level}</span></div><h3>{lesson.title}</h3><p>{lesson.topic} · {lesson.date}</p><div className="tile-bottom">进入课程 <ArrowRight size={17}/></div></button>}
function PanelHeader({step,title,desc}:{step:string;title:string;desc:string}){return <div className="panel-heading"><div><span className="eyebrow">{step}</span><h2>{title}</h2><p className="panel-desc">{desc}</p></div></div>}

createRoot(document.getElementById('root')!).render(<App/>);
