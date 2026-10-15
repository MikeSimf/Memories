const fs=require('fs'), path=require('path');
const MAIN='/data/external/dnd-memory/android/app/src/main';
const RES=path.join(MAIN,'res'), JAVA=path.join(MAIN,'java');
const GENERATED=new Set(['R','BuildConfig']);
const LIB_STRINGS=new Set(['appbar_scrolling_view_behavior']);

function walk(dir, ext, out=[]){
  if(!fs.existsSync(dir)) return out;
  for(const e of fs.readdirSync(dir,{withFileTypes:true})){
    const p=path.join(dir,e.name);
    if(e.isDirectory()) walk(p,ext,out); else if(e.name.endsWith(ext)) out.push(p);
  }
  return out;
}
const read=f=>fs.readFileSync(f,'utf8');
const camelToSnake=s=>s.replace(/([a-z0-9])([A-Z])/g,'$1_$2').replace(/([A-Z]+)([A-Z][a-z])/g,'$1_$2').toLowerCase();

const layoutFiles=walk(path.join(RES,'layout'),'.xml');
const menuFiles=walk(path.join(RES,'menu'),'.xml');
const allResXml=walk(RES,'.xml');
const drawables=new Set(walk(path.join(RES,'drawable'),'.xml').map(f=>path.basename(f,'.xml')));
const mipmaps=new Set();
for(const d of fs.readdirSync(RES)) if(d.startsWith('mipmap-')) for(const f of fs.readdirSync(path.join(RES,d))) mipmaps.add(f.replace(/\.(xml|png|webp)$/,''));
const menus=new Set(menuFiles.map(f=>path.basename(f,'.xml')));
const layouts=new Set(layoutFiles.map(f=>path.basename(f,'.xml')));
const xmls=new Set(walk(path.join(RES,'xml'),'.xml').map(f=>path.basename(f,'.xml')));
const strings=new Set([...read(path.join(RES,'values/strings.xml')).matchAll(/<string name="([^"]+)"/g)].map(m=>m[1]));
const colors=new Set([...read(path.join(RES,'values/colors.xml')).matchAll(/<color name="([^"]+)"/g)].map(m=>m[1]));
const styles=new Set([...read(path.join(RES,'values/themes.xml')).matchAll(/<style name="([^"]+)"/g)].map(m=>m[1]));

const idsRes=new Map();
for(const f of [...layoutFiles,...menuFiles])
  for(const m of read(f).matchAll(/@\+id\/([A-Za-z0-9_]+)/g)) idsRes.set(m[1], path.relative(RES,f));

const javaFiles=walk(JAVA,'.java');
const problems=[];
const check=(kind,name,where)=>{
  if(kind==='string'){ if(!strings.has(name) && !LIB_STRINGS.has(name)) problems.push(`@string/${name} (${where}) нет в strings.xml`); return; }
  if(kind==='drawable'){ if(!drawables.has(name)) problems.push(`@drawable/${name} (${where}) нет в res/drawable`); return; }
  if(kind==='color'){ if(!colors.has(name)) problems.push(`@color/${name} (${where}) нет в colors.xml`); return; }
  if(kind==='menu'){ if(!menus.has(name)) problems.push(`@menu/${name} (${where}) нет в res/menu`); return; }
  if(kind==='layout'){ if(!layouts.has(name)) problems.push(`@layout/${name} (${where}) нет в res/layout`); return; }
  if(kind==='xml'){ if(!xmls.has(name)) problems.push(`@xml/${name} (${where}) нет в res/xml`); return; }
  if(kind==='mipmap'){ if(!mipmaps.has(name)) problems.push(`@mipmap/${name} (${where}) нет`); return; }
  if(kind==='style'){ if(!styles.has(name)) problems.push(`@style/${name} (${where}) не найден`); return; }
};

// 1. R.id / R.string / ... из Java
const usedIds=new Map();
for(const f of javaFiles){
  const raw=read(f);
  const src=raw.replace(/android\.R\.id\.[A-Za-z0-9_]+/g,'').split('\n').filter(l=>!l.trim().startsWith('import ')).join('\n');
  const rel=path.relative(JAVA,f);
  for(const m of src.matchAll(/R\.id\.([A-Za-z0-9_]+)/g)) usedIds.set(m[1], rel);
  for(const m of src.matchAll(/R\.(string|drawable|color|menu|layout|xml|mipmap)\.([A-Za-z0-9_]+)/g)) check(m[1],m[2],rel);
}
for(const [id,file] of usedIds) if(!idsRes.has(id)) problems.push(`R.id.${id} используется в ${file} — такого id нет ни в одном layout/menu`);

// 2. ссылки внутри res/*.xml
for(const f of allResXml){
  const rel=path.relative(RES,f);
  for(const m of read(f).matchAll(/@(string|drawable|color|menu|layout|xml|mipmap|style)\/([A-Za-z0-9_.]+)/g)) check(m[1],m[2],rel);
}

// 3. import ru.dndmemory.*
for(const f of javaFiles){
  const src=read(f), rel=path.relative(JAVA,f);
  for(const m of src.matchAll(/^import\s+(?:static\s+)?ru\.dndmemory\.([A-Za-z0-9_.]+);/gm)){
    const parts=m[1].split('.');
    const last=parts[parts.length-1];
    if(GENERATED.has(last) || parts[0]==='databinding') continue;
    if(!/^[A-Z]/.test(last)) continue;
    const file=path.join(JAVA,'ru/dndmemory',parts.slice(0,-1).join('/'),last+'.java');
    if(!fs.existsSync(file)){
      const owner=path.join(JAVA,'ru/dndmemory',parts.slice(0,-2).join('/'),parts[parts.length-2]+'.java');
      if(!fs.existsSync(owner)) problems.push(`import ru.dndmemory.${m[1]} в ${rel}: файла нет`);
    }
  }
}

// 4. манифест
const manifest=read(path.join(MAIN,'AndroidManifest.xml'));
for(const m of manifest.matchAll(/android:name="\.([A-Za-z0-9_.]+)"/g)){
  const file=path.join(JAVA,'ru/dndmemory',m[1].split('.').join('/')+'.java');
  if(!fs.existsSync(file)) problems.push(`манифест: ${m[1]} — класса нет`);
}
for(const m of manifest.matchAll(/@(string|drawable|color|menu|layout|xml|mipmap|style)\/([A-Za-z0-9_.]+)/g)) check(m[1],m[2],'AndroidManifest.xml');

// 5. viewBinding
let bindingFields=0, bindingClasses=0;
for(const f of javaFiles){
  const raw=read(f);
  const src=raw.split('\n').filter(l=>!l.trim().startsWith('import ')).join('\n');
  const rel=path.relative(JAVA,f);
  const imports=[...raw.matchAll(/import\s+ru\.dndmemory\.databinding\.([A-Za-z0-9_]+)Binding;/g)].map(x=>x[1]);
  for(const cls of imports){
    bindingClasses++;
    const layout=camelToSnake(cls);
    const layoutPath=path.join(RES,'layout',layout+'.xml');
    if(!fs.existsSync(layoutPath)){ problems.push(`${rel}: ${cls}Binding → нет layout/${layout}.xml`); continue; }
    const layoutIds=new Set([...read(layoutPath).matchAll(/@\+id\/([A-Za-z0-9_]+)/g)].map(x=>x[1]));
    const vars=new Set();
    for(const x of src.matchAll(new RegExp('([A-Za-z0-9_]+)\\s*=\\s*'+cls+'Binding\\.inflate','g'))) vars.add(x[1]);
    for(const x of src.matchAll(new RegExp('\\b'+cls+'Binding\\s+([A-Za-z0-9_]+)\\s*=','g'))) vars.add(x[1]);
    for(const v of vars){
      for(const fm of src.matchAll(new RegExp('\\b'+v+'\\.([A-Za-z0-9_]+)','g'))){
        const field=fm[1];
        if(field==='getRoot' || /^[A-Z]/.test(field)) continue;
        bindingFields++;
        const id=camelToSnake(field);
        if(!layoutIds.has(id)) problems.push(`${rel}: ${v}.${field} → в layout/${layout}.xml нет id ${id}`);
      }
    }
  }
}

console.log('=== САМОПРОВЕРКА DnD Memory (Android) ===');
console.log('java:', javaFiles.length, '| layout:', layoutFiles.length, '| menu:', menuFiles.length,
            '| drawable:', drawables.size, '| mipmap:', mipmaps.size);
console.log('strings:', strings.size, '| colors:', colors.size, '| id@res:', idsRes.size, '| R.id@java:', usedIds.size);
console.log('ViewBinding-классов использовано:', bindingClasses, '| полей проверено:', bindingFields);
console.log('');
if(problems.length===0){ console.log('*** РАСХОЖДЕНИЙ НЕ НАЙДЕНО ***'); }
else {
  fs.writeFileSync('/data/external/dnd-memory/android/.check-report.txt', problems.join('\n'));
  console.log('*** РАСХОЖДЕНИЯ: '+problems.length+' ***');
  for(const p of problems.slice(0,50)) console.log(' - '+p);
  if(problems.length>50) console.log(' ... ещё '+(problems.length-50)+' → .check-report.txt');
}
