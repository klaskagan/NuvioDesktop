#!/usr/bin/env python3
"""Prepare a fresh upstream checkout with our receiver overlay; never installs apps."""
import argparse,json,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
MANIFEST=Path(__file__).with_name("receivers.json")
def run(args,**kw):
    return subprocess.run(args,check=True,**kw)
def latest_tag(url):
    output=subprocess.check_output(["git","ls-remote","--tags","--refs",url],text=True)
    tags=[line.split()[1].removeprefix("refs/tags/") for line in output.splitlines()]
    versions=[(tuple(map(int,m.groups())),tag) for tag in tags if (m:=re.fullmatch(r"v?(\d+)\.(\d+)\.(\d+)",tag))]
    if not versions: raise RuntimeError("No stable version tags found")
    return max(versions)[1]
def prepare(config,ref,destination,test=False):
    if not ref or ref.startswith("-"): raise ValueError("Invalid upstream ref")
    destination=Path(destination).resolve()
    if destination.exists(): raise ValueError("Destination must be a new directory; existing work is never reset")
    run(["git","clone","--filter=blob:none","--no-checkout",config["url"],str(destination)])
    run(["git","fetch","origin",ref],cwd=destination)
    run(["git","checkout","--detach","FETCH_HEAD"],cwd=destination)
    patch=ROOT/"docs"/"tv-handoff"/config["patch"]
    # A conflict leaves only this fresh checkout for inspection, never the installed apps.
    run(["git","apply","--3way",str(patch)],cwd=destination)
    unresolved=subprocess.check_output(["git","diff","--name-only","--diff-filter=U"],cwd=destination,text=True)
    if unresolved.strip(): raise RuntimeError("Receiver overlay conflicts with upstream; review required")
    run(["git","diff","--cached","--check"],cwd=destination)
    if test:
        for command in config["tests"]: run(command,cwd=destination)
    print("Receiver overlay prepared:",destination)
    print("Next: supply local build configuration, build and test the package. No app was installed.")
def export_overlay(receiver,source,ref=None):
    configs=json.loads(MANIFEST.read_text());c=configs[receiver];source=Path(source).resolve()
    ref=ref or c["base"]
    if ref.startswith("-"):raise ValueError("Invalid base ref")
    base=subprocess.check_output(["git","rev-parse","--verify",ref+"^{commit}"],cwd=source,text=True).strip()
    data=subprocess.check_output(["git","diff","--binary","--full-index",base,"--"],cwd=source)
    untracked=subprocess.check_output(["git","ls-files","--others","--exclude-standard"],cwd=source,text=True).splitlines()
    for name in untracked:
        if "handoff" not in name.lower():continue
        r=subprocess.run(["git","diff","--no-index","--binary","--full-index","/dev/null",name],cwd=source,stdout=subprocess.PIPE)
        if r.returncode not in (0,1):raise RuntimeError("Could not export "+name)
        data+=r.stdout
    if not data:raise ValueError("No receiver changes relative to selected base")
    patch=ROOT/"docs"/"tv-handoff"/c["patch"]
    temporary=patch.with_suffix(".patch.new");temporary.write_bytes(data);temporary.replace(patch)
    c["base"]=base;MANIFEST.write_text(json.dumps(configs,indent=2)+"\n")
    readme=ROOT/"docs"/"tv-handoff"/"README.md"
    lines=readme.read_text().splitlines()
    for i,line in enumerate(lines):
        cells=line.split("|")
        if len(cells)==5 and cells[3].strip()==c["patch"]:
            cells[2]=" "+base+" ";lines[i]="|".join(cells)
    readme.write_text("\n".join(lines)+"\n")
    print("Exported overlay:",patch,"base:",base)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("action",choices=["check","prepare","export"])
    p.add_argument("--receiver",choices=["android","webos","legacy"]);p.add_argument("--ref");p.add_argument("--destination",type=Path);p.add_argument("--test",action="store_true");p.add_argument("--source",type=Path)
    a=p.parse_args();configs=json.loads(MANIFEST.read_text())
    if a.action=="check":
        for name,c in configs.items():
            if a.receiver and name!=a.receiver:continue
            print(name,"latest stable:",latest_tag(c["url"]),"overlay base:",c["base"])
    elif a.action=="export":
        if not a.receiver or not a.source:p.error("export requires --receiver and --source")
        export_overlay(a.receiver,a.source,a.ref)
    else:
        if not a.receiver or not a.destination:p.error("prepare requires --receiver and --destination")
        c=configs[a.receiver];prepare(c,a.ref or latest_tag(c["url"]),a.destination,a.test)
if __name__=="__main__":
    try:main()
    except (ValueError,RuntimeError,subprocess.CalledProcessError) as e:
        print("Update preparation stopped:",e,file=sys.stderr);sys.exit(1)
