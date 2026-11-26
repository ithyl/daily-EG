import {useCallback,useEffect,useRef,useState} from 'react';

export function useAudioRecorder(){
 const [recording,setRecording]=useState(false);
 const [note,setNote]=useState('');
 const [audioBlob,setAudioBlob]=useState<Blob|null>(null);
 const recorder=useRef<MediaRecorder|null>(null);
 const stream=useRef<MediaStream|null>(null);
 const start=useCallback(async()=>{
  if(!navigator.mediaDevices?.getUserMedia||!window.MediaRecorder){setNote('当前浏览器不支持录音，请使用 HTTPS 或 localhost。');return;}
  try{
   const media=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true}});
   stream.current=media;
   const mime=['audio/webm;codecs=opus','audio/mp4','audio/webm','audio/ogg'].find(x=>MediaRecorder.isTypeSupported(x));
   const rec=new MediaRecorder(media,mime?{mimeType:mime}:undefined);
   const chunks:BlobPart[]=[];
   rec.ondataavailable=e=>{if(e.data.size)chunks.push(e.data)};
   rec.onstop=()=>{
    setAudioBlob(new Blob(chunks,{type:(rec.mimeType||'audio/webm').split(';')[0]}));
    stream.current?.getTracks().forEach(t=>t.stop());stream.current=null;
    setRecording(false);setNote('录音已完成。点击“提交录音并获取反馈”。');
   };
   rec.onerror=()=>{setNote('录音失败，请重试');setRecording(false);media.getTracks().forEach(t=>t.stop())};
   recorder.current=rec;setAudioBlob(null);rec.start(200);setRecording(true);setNote('录音中…请朗读或自由表达（建议30–60秒）。');
  }catch{setNote('无法访问麦克风，请检查浏览器权限');}
 },[]);
 const stop=useCallback(()=>{if(recorder.current?.state==='recording')recorder.current.stop()},[]);
 const clear=useCallback(()=>{setAudioBlob(null);setNote('')},[]);
 useEffect(()=>()=>{if(recorder.current?.state==='recording')recorder.current.stop();stream.current?.getTracks().forEach(t=>t.stop())},[]);
 return {recording,note,audioBlob,start,stop,clear};
}
