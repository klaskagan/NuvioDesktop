const {app,BrowserWindow}=require('electron');
const fs=require('node:fs');
const path=require('node:path');
const {discoverEntries}=require('./catalog.cjs');
const root=process.env.NUVIO_GO3_PROBE_DIR;
if(!root)process.exit(2);
app.setPath('userData',path.join(root,'public-discovery-runtime'));
app.whenReady().then(async()=>{
 try {
  const view=new BrowserWindow({show:false,webPreferences:{partition:'public-discovery',sandbox:true,contextIsolation:true,nodeIntegration:false}});
  const entries=await discoverEntries(view);
  fs.writeFileSync(path.join(root,'public-discovery.json'),JSON.stringify(entries,null,2),{mode:0o600});
  console.log('Public discovery completed:',entries.length);
 }catch{console.log('Public discovery failed');}
 app.quit();
});
