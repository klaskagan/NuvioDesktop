// Integration check against official public catalogue pages. Prints counts only.
const {spawn}=require('node:child_process');const fs=require('node:fs');const os=require('node:os');const path=require('node:path');const readline=require('node:readline');
const executable=process.env.NUVIO_GO3_ELECTRON;if(!executable)throw new Error('Set NUVIO_GO3_ELECTRON');
const child=spawn(executable,__dirname? [__dirname]:[],{env:{...process.env,NUVIO_GO3_SERVICE:'1',NUVIO_GO3_PROBE_DIR:fs.mkdtempSync(path.join(os.tmpdir(),'go3-service-check-'))},stdio:['pipe','pipe','ignore']});
const timer=setTimeout(()=>{child.kill();process.exitCode=1;},90000);
let completed=0;readline.createInterface({input:child.stdout}).on('line',line=>{try{const e=JSON.parse(line);if(e.kind==='runtime'){console.log(JSON.stringify({dock_visible:e.dock_visible,app_name:e.app_name,player_hidden:e.player_hidden,player_display_sized:e.player_display_sized}));return;}if(e.kind!=='events')return;
if(e.error){console.log('Catalogue failed:',e.request);process.exitCode=1;}else console.log(JSON.stringify({catalog:e.request,count:e.entries.length,load_ms:e.load_ms,artwork:e.entries.filter(x=>x.artwork).length,states:[...new Set(e.entries.map(x=>x.state))],programmeLinks:e.entries.every(x=>x.url.includes('programme-'))}));
if(++completed===2){clearTimeout(timer);child.stdin.end(JSON.stringify({action:'quit'})+'\n');}}catch{}});
child.on('exit',()=>{clearTimeout(timer);if(completed!==2)process.exitCode=1;});
child.stdin.write(JSON.stringify({action:'events',catalog:'live',request:'live'})+'\n');
// Serialize requests: the service catalogue browser must never navigate two pages concurrently.
child.stdin.write(JSON.stringify({action:'events',catalog:'epl',request:'epl'})+'\n');
