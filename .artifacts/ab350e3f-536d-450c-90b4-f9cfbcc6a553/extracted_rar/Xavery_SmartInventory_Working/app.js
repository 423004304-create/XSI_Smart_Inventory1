const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
let state = { products: [], movements: [], users: [], attendance: [] }, currentUser = null, currentPage = "dashboard", stream = null, lastDetected = null, attendanceSyncError = "";
function esc(s){return String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]))}
function money(n){return new Intl.NumberFormat("en-PH",{style:"currency",currency:"PHP",maximumFractionDigits:2}).format(Number(n)||0)}
function compactMoney(n){const value=Number(n)||0;if(value>=1000000)return `₱${(value/1000000).toFixed(1)}m`;if(value>=1000)return `₱${(value/1000).toFixed(1)}k`;return value?money(value):"₱0"}
function dateFmt(d){return (typeof d?.toDate==="function"?d.toDate():new Date(d)).toLocaleString("en-PH",{dateStyle:"medium",timeStyle:"short"})}
function dateKey(d=new Date()){return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`}
function attendanceTime(record){return record?.timeIn?.toDate?.()||new Date(record?.timeIn)}
function stockStatus(p){return p.quantity<=0?["Out of stock","out"]:p.quantity<=p.minStock?["Low stock","low"]:["In stock","ok"]}
function normalizeRole(role){
  if(role==="Inventory Clerk")return "Staff";
  if(role==="Admin"||role==="Administrator")return "Administrator";
  if(role==="Project Admin"||role==="Project Administrator")return "Project Administrator";
  return role;
}
function canEdit(){return currentUser?.role==="Administrator"||currentUser?.role==="Project Administrator"}
function canManageAdmins(){return currentUser?.role==="Project Administrator"}
function staffLandingPage(){return currentUser?.role==="Sales Staff"?"pos":"attendance"}
function toast(msg){const el=$("#toast");el.textContent=msg;el.classList.add("show");setTimeout(()=>el.classList.remove("show"),2600)}
function init(){
 $("#loginForm").addEventListener("submit",e=>{e.preventDefault();const email=$("#loginEmail").value.trim().toLowerCase(),password=$("#loginPassword").value;const demo=DEMO_USERS.find(u=>u.email===email&&u.password===password);const user=state.users.find(u=>u.email.toLowerCase()===email);if(demo&&password===demo.password||user&&demo){currentUser=state.users.find(u=>u.email.toLowerCase()===email)||demo;showApp()}else $("#loginError").textContent="Invalid demo credentials. Use one of the demo accounts shown below."});
 $("#logoutBtn").onclick=()=>{stopCamera();currentUser=null;$("#app").classList.add("hidden");$("#loginScreen").classList.remove("hidden")};
 $$(".nav").forEach(b=>b.onclick=()=>navigate(b.dataset.page));
 $("#topAddBtn").onclick=()=>openProductModal();
 showLoginDefaults();
}
function showLoginDefaults(){/* Demo credentials are intentionally local-only. */}
function showApp(){
 $("#loginScreen").classList.add("hidden");$("#app").classList.remove("hidden");
 $("#sideUser").textContent=currentUser.name;
 $("#sideRole").textContent=currentUser.role==="Project Administrator"?"Project Admin":canEdit()?"Admin":currentUser.role;
 $("#userAvatar").textContent=currentUser.name.charAt(0).toUpperCase();
 $$(".nav").forEach(el=>{
   const page=el.dataset.page;
   const visible=canEdit()
     ?page!=="pos"&&page!=="attendance"
     :currentUser.role==="Staff"
       ?page==="attendance"
       :currentUser.role==="Sales Staff"&&(page==="pos"||page==="attendance");
   el.classList.toggle("hidden",!visible);
 });
 $("#topAddBtn").classList.toggle("hidden",!canEdit());navigate(canEdit()?"dashboard":staffLandingPage());
}
function navigate(page){
 const allowedPages=canEdit()
   ?["dashboard","inventory","stickers","movements","reports","prices","analytics","users"]
   :currentUser?.role==="Sales Staff"?["pos","attendance"]:["attendance"];
 if(!allowedPages.includes(page)){
   toast("This page is not available for your role.");
   navigate(canEdit()?"dashboard":staffLandingPage());
   return;
 }
 stopCamera();currentPage=page;$$(".nav").forEach(b=>b.classList.toggle("active",b.dataset.page===page));
 const meta={
   dashboard:["OVERVIEW","Dashboard"],inventory:["STOCK CONTROL","Inventory"],
   stickers:["LABEL PRINTING","Sticker Labels"],
   movements:["AUDIT TRAIL","Stock Movements"],
   reports:["INSIGHTS","Reports"],users:["ACCESS CONTROL","Users"],
   pos:["POINT OF SALE","Point of Sale"],prices:["CATALOG CONTROL","Price Management"],
   analytics:["BUSINESS INSIGHTS","Analytics"],attendance:["EMPLOYEE WORKSPACE","Time Clock"]
 }[page];
 $("#pageEyebrow").textContent=meta[0];
 $("#pageTitle").textContent=meta[1];
 $("#topAddBtn").classList.toggle("hidden",!canEdit()||!["dashboard","inventory"].includes(page));
 renderPage();
}
function renderPage() {
  const target = $("#pageContent");
  if(stream)stopCamera();
  const pages = {
    dashboard: dashboardPage,
    inventory: inventoryPage,
    stickers: stickerPage,
    movements: movementsPage,
    reports: reportsPage,
    users: usersPage,
    pos: posPage,
    attendance: attendancePage,
    prices: pricesPage,
    analytics: analyticsPage
  };
  target.innerHTML = (pages[currentPage] || dashboardPage)();
  wirePage();
}
function stats(){const total=state.products.length,units=state.products.reduce((a,p)=>a+p.quantity,0),low=state.products.filter(p=>p.quantity>0&&p.quantity<=p.minStock).length,out=state.products.filter(p=>p.quantity<=0).length;return {total,units,low,out,value:state.products.reduce((a,p)=>a+p.quantity*p.price,0)}}
function statCard(label,value,note,icon){return `<div class="stat"><div class="stat-top"><span>${label}</span><span class="stat-icon">${icon}</span></div><div class="stat-value">${value}</div><div class="stat-note">${note}</div></div>`}
function productRows(list,showActions=true){if(!list.length)return `<tr><td colspan="7"><div class="empty">No motorcycle parts found.</div></td></tr>`;return list.map(p=>{const [status,cl]=stockStatus(p);return `<tr><td><div class="part-cell">${p.imageUrl?`<img class="part-photo-thumb" src="${esc(p.imageUrl)}" alt="${esc(p.name)}">`:`<div class="part-icon">⚙</div>`}<div><b>${esc(p.name)}</b><small>${esc(p.sku)}</small></div></div></td><td>${esc(p.category)}</td><td><b>${p.quantity}</b></td><td>${money(p.price)}</td><td><span class="pill ${cl}">${status}</span></td><td>${esc(p.location||"—")}</td>${showActions?`<td><div class="actions"><button class="icon-btn" data-edit="${esc(p.id)}" title="Edit">✎</button>${canEdit()?`<button class="icon-btn" data-delete="${esc(p.id)}" title="Delete">×</button>`:""}</div></td>`:""}</tr>`}).join("")}
function dashboardPage(){
 const s=stats(),low=state.products.filter(p=>p.quantity<=p.minStock).sort((a,b)=>a.quantity-b.quantity),recent=[...state.movements].sort((a,b)=>new Date(b.date)-new Date(a.date)).slice(0,5);
 return `<div class="page"><div class="stats">${statCard("PART TYPES",s.total,"Unique catalog items","⚙")}${statCard("UNITS IN STOCK",s.units,"Across all part types","▤")}${statCard("LOW STOCK",s.low,"Needs replenishment","⚠")}${statCard("INVENTORY VALUE",money(s.value),"Based on current unit prices","₱")}</div><div class="content-grid"><div class="panel"><div class="section-head"><h3>Inventory overview</h3>${canEdit()?`<button class="secondary" data-goto="inventory">View all →</button>`:""}</div><p class="panel-sub">Current motorcycle parts and availability</p><div class="table-wrap"><table><thead><tr><th>Part / SKU</th><th>Category</th><th>Qty</th><th>Unit price</th><th>Status</th><th>Location</th></tr></thead><tbody>${productRows([...state.products].slice(0,6),false)}</tbody></table></div></div><div class="panel"><div class="section-head"><h3>Stock alerts</h3><span class="pill ${low.length?"low":"ok"}">${low.length} items</span></div><p class="panel-sub">Items at or below their minimum stock</p><div class="alert-list">${low.length?low.map(p=>`<div class="alert-item"><span class="alert-sign">⚠</span><div><b>${esc(p.name)}</b><small>${p.quantity} remaining · minimum ${p.minStock}</small></div></div>`).join(""):`<div class="empty">All items are above their minimum stock.</div>`}</div>${canEdit()?`<div style="height:22px"></div><div class="section-head"><h3>Recent movements</h3><button class="secondary" data-goto="movements">View →</button></div>${recent.length?recent.slice(0,3).map(m=>`<div class="report-card"><div><b>${esc(m.productName)}</b><small>${dateFmt(m.date)}</small></div><span class="pill ${m.type==="IN"?"in":"outgoing"}">${m.type==="IN"?"+":"−"}${m.quantity}</span></div>`).join(""):`<div class="empty">No movement records yet.</div>`}`:""}</div></div></div>`;
}
function inventoryPage(){return `<div class="page"><div class="toolbar"><input id="productSearch" placeholder="Search name, SKU, category…"><select id="stockFilter"><option value="">All stock statuses</option><option value="ok">In stock</option><option value="low">Low stock</option><option value="out">Out of stock</option></select><div class="spacer"></div></div><div class="panel"><div class="section-head"><div><h3>Motorcycle parts catalog</h3><p class="panel-sub">Manage product details and stock thresholds</p></div><span class="pill ok">${state.products.length} items</span></div><div class="table-wrap"><table><thead><tr><th>Part / SKU</th><th>Category</th><th>Qty</th><th>Unit price</th><th>Status</th><th>Location</th><th>Actions</th></tr></thead><tbody id="productTable">${productRows(state.products)}</tbody></table></div></div></div>`}
function stickerLabel(product){return `<div class="sticker-label"><div class="sticker-brand">XAVERY <span>SMART INVENTORY</span></div><div class="sticker-label-name">${esc(product.name)}</div><div class="sticker-label-category">${esc(product.category||"Motorcycle part")}</div><div class="sticker-label-bottom"><span>SKU <b>${esc(product.sku)}</b></span><strong>${money(product.price)}</strong></div>${product.location?`<div class="sticker-label-location">${esc(product.location)}</div>`:""}</div>`}
function stickerPage(){
 const cards=state.products.map(product=>`<article class="sticker-product" data-sticker-product data-search="${esc(`${product.name} ${product.sku} ${product.category}`.toLowerCase())}">
   <div class="sticker-product-head"><label class="sticker-select"><input type="checkbox" data-sticker-select="${esc(product.id)}"> Select item</label><span class="muted">${esc(product.sku)}</span></div>
   <div class="sticker-preview">${stickerLabel(product)}</div>
   <div class="sticker-product-actions"><label>Copies<input type="number" min="1" max="100" step="1" value="1" data-sticker-copies="${esc(product.id)}"></label><button class="secondary" type="button" data-print-single="${esc(product.id)}">Print item</button></div>
 </article>`).join("");
 return `<div class="page sticker-page"><div class="panel sticker-toolbar"><div><h3>Generate item stickers</h3><p class="panel-sub">Choose inventory items and the number of copies to print. Labels include the item name, SKU, category, price, and location.</p></div><div class="sticker-toolbar-actions"><label class="sticker-select"><input id="selectAllStickers" type="checkbox"> Select visible</label><span id="stickerSelectedCount" class="muted">0 selected</span><button id="printSelectedStickers" class="primary" type="button" disabled>Print selected</button></div></div><div class="toolbar sticker-search"><input id="stickerSearch" type="search" placeholder="Search name, SKU, or category…"><span class="muted">${state.products.length} inventory items</span></div><div id="stickerGrid" class="sticker-grid">${cards||`<div class="panel empty">No inventory items yet. Add items in Inventory to create stickers.</div>`}</div></div>`;
}
function selectedStickerItems(){
 return $$("[data-sticker-select]:checked").map(checkbox=>{
   const product=state.products.find(item=>item.id===checkbox.dataset.stickerSelect);
   const copiesInput=$$("[data-sticker-copies]").find(input=>input.dataset.stickerCopies===checkbox.dataset.stickerSelect);
   return {product,copies:Number(copiesInput?.value)};
 });
}
function printStickerItems(items){
 if(!items.length){toast("Select at least one item to print.");return}
 if(items.some(item=>!item.product||!Number.isInteger(item.copies)||item.copies<1||item.copies>100)){
   toast("Each item must have between 1 and 100 sticker copies.");
   return;
 }
 const area=$("#stickerPrintArea");
 area.innerHTML=items.map(({product,copies})=>stickerLabel(product).repeat(copies)).join("");
 window.print();
}
function wireStickerPage(){
 const search=$("#stickerSearch"),cards=$$("[data-sticker-product]"),selectAll=$("#selectAllStickers"),printSelected=$("#printSelectedStickers");
 const updateSelection=()=>{
   const selected=$$("[data-sticker-select]:checked").length;
   const visible=cards.filter(card=>!card.classList.contains("hidden"));
   const selectedVisible=visible.filter(card=>card.querySelector("[data-sticker-select]").checked).length;
   $("#stickerSelectedCount").textContent=`${selected} selected`;
   printSelected.disabled=selected===0;
   selectAll.checked=visible.length>0&&selectedVisible===visible.length;
   selectAll.indeterminate=selectedVisible>0&&selectedVisible<visible.length;
 };
 const filterCards=()=>{
   const query=search.value.trim().toLowerCase();
   cards.forEach(card=>card.classList.toggle("hidden",!card.dataset.search.includes(query)));
   updateSelection();
 };
 search.oninput=filterCards;
 selectAll.onchange=()=>{
   cards.filter(card=>!card.classList.contains("hidden")).forEach(card=>{card.querySelector("[data-sticker-select]").checked=selectAll.checked});
   updateSelection();
 };
 $$("[data-sticker-select]").forEach(checkbox=>checkbox.onchange=updateSelection);
 printSelected.onclick=()=>printStickerItems(selectedStickerItems());
 $$("[data-print-single]").forEach(button=>button.onclick=()=>{
   const product=state.products.find(item=>item.id===button.dataset.printSingle);
   const copiesInput=$$("[data-sticker-copies]").find(input=>input.dataset.stickerCopies===button.dataset.printSingle);
   printStickerItems([{product,copies:Number(copiesInput?.value)}]);
 });
 updateSelection();
}
function scannerPanel(forSale=false){return `<div class="scanner-grid"><div class="panel"><h3>${forSale?"Scan a part for this sale":"Live part scanner"}</h3><p class="panel-sub">Use the device camera to capture a motorcycle part.</p><div class="camera-box" id="cameraBox"><div class="camera-placeholder"><div class="camera-icon">◎</div><b>Camera is not active</b><p>Start the camera to scan a part.</p></div></div><div class="camera-controls"><button class="primary" id="startCamera">Start camera</button><button class="secondary" id="captureBtn" disabled>Capture image</button><button class="secondary" id="stopCameraBtn" disabled>Stop camera</button><input type="file" id="imageUpload" accept="image/*" capture="environment" hidden><button class="secondary" id="uploadImage">Upload image</button></div><canvas id="captureCanvas" hidden></canvas><p class="hint">Camera access requires HTTPS or localhost and permission from your browser.</p></div><div class="panel"><h3>Detection result</h3><p class="panel-sub">${forSale?"Confirm the matching item and add it to the cart.":"Review a detected item before changing stock."}</p><div id="detectionResult" class="scan-result"><b>No scan yet</b><p>Capture or upload an image. Connect the optional YOLOv8 API for actual model-based recognition. This demo does not pretend to identify objects without a trained model.</p></div><div class="note">${forSale?"A scanned part is added to the cart; stock changes only when the sale is completed.":"Always confirm the part and quantity before saving a stock movement."} Detection results depend on the trained YOLOv8 model and image quality.</div><div id="scanActions" class="camera-controls"></div></div></div>`}
function movementsPage(){return `<div class="page"><div class="toolbar"><input id="movementSearch" placeholder="Search part or user…"><select id="movementFilter"><option value="">All movements</option><option value="IN">Stock in</option><option value="OUT">Stock out</option></select><div class="spacer"></div><button class="primary" id="newMovement">＋ Record movement</button></div><div class="panel"><div class="section-head"><h3>Inventory audit trail</h3><span class="pill ok">${state.movements.length} records</span></div><div class="table-wrap"><table><thead><tr><th>Movement ID</th><th>Part</th><th>Type</th><th>Quantity</th><th>Performed by</th><th>Date</th><th>Note</th></tr></thead><tbody id="movementTable">${movementRows(state.movements)}</tbody></table></div></div></div>`}
function movementRows(list){if(!list.length)return `<tr><td colspan="7"><div class="empty">No stock movements recorded.</div></td></tr>`;return [...list].sort((a,b)=>new Date(b.date)-new Date(a.date)).map(m=>`<tr><td>${esc(m.id)}</td><td><b>${esc(m.productName)}</b></td><td><span class="pill ${m.type==="IN"?"in":"outgoing"}">${m.type==="IN"?"Stock in":"Stock out"}</span></td><td><b>${m.type==="IN"?"+":"−"}${m.quantity}</b></td><td>${esc(m.user)}</td><td>${dateFmt(m.date)}</td><td>${esc(m.note||"—")}</td></tr>`).join("")}
function attendancePage(){
 const records=state.attendance.filter(record=>canEdit()||record.employeeId===currentUser.id).sort((a,b)=>attendanceTime(b)-attendanceTime(a));
 const myRecords=records.filter(record=>record.employeeId===currentUser.id);
 const openShift=myRecords.find(record=>!record.timeOut);
 const today=dateKey();
 const todayRecord=myRecords.find(record=>record.date===today);
 const activeRecord=openShift||null;
 const canTimeIn=!activeRecord&&!todayRecord;
 const status=activeRecord?"On shift":todayRecord?"Shift complete":"Not clocked in";
 const timeIn=activeRecord||todayRecord;
 const actionLabel=activeRecord?"Time Out":todayRecord?"Shift complete":"Time In";
 const buttonDisabled=(!activeRecord&&!canTimeIn)||attendanceActionInProgress||Boolean(attendanceSyncError);
 const rows=records.length?records.map(record=>`<tr><td>${esc(new Date(`${record.date}T00:00:00`).toLocaleDateString("en-PH",{dateStyle:"medium"}))}</td><td>${esc(record.employeeName)}</td><td>${dateFmt(record.timeIn)}</td><td>${record.timeOut?dateFmt(record.timeOut):'<span class="pill in">On shift</span>'}</td></tr>`).join(""):`<tr><td colspan="4"><div class="empty">No attendance records yet.</div></td></tr>`;
 return `<div class="page">${attendanceSyncError?`<div class="panel attendance-warning" role="alert"><b>Time Clock unavailable</b><p>${esc(attendanceSyncError)}</p></div>`:""}<div class="attendance-layout"><div class="panel attendance-card"><div><p class="eyebrow">TODAY · ${esc(new Date().toLocaleDateString("en-PH",{dateStyle:"full"}))}</p><h3>Your work shift</h3><span class="pill ${activeRecord?"in":todayRecord?"ok":"low"}">${status}</span><p class="panel-sub attendance-message">${activeRecord?`Clocked in at ${dateFmt(activeRecord.timeIn)}${activeRecord.date!==today?` · Started ${esc(activeRecord.date)}`:""}.`:todayRecord?`Time in: ${dateFmt(todayRecord.timeIn)} · Time out: ${dateFmt(todayRecord.timeOut)}.`:"Clock in when you begin your shift. Remember to clock out when you finish."}</p></div><div class="attendance-punches"><div><small>TIME IN</small><b>${timeIn?dateFmt(timeIn.timeIn):"—"}</b></div><div><small>TIME OUT</small><b>${activeRecord?"In progress":todayRecord?.timeOut?dateFmt(todayRecord.timeOut):"—"}</b></div><button class="primary" id="attendanceAction" ${buttonDisabled?"disabled":""} data-action="${activeRecord?"out":"in"}">${attendanceActionInProgress?"Saving…":actionLabel}</button></div></div></div><div class="panel attendance-history"><div class="section-head"><div><h3>${canEdit()?"Employee attendance":"My attendance"}</h3><p class="panel-sub">${canEdit()?"Recent time-in and time-out records":"Your recent time-in and time-out records"}</p></div><span class="pill ok">${records.length} records</span></div><div class="table-wrap"><table><thead><tr><th>Date</th><th>Employee</th><th>Time in</th><th>Time out</th></tr></thead><tbody>${rows}</tbody></table></div></div></div>`;
}
function reportsPage(){const s=stats(),top=[...state.products].sort((a,b)=>b.quantity-a.quantity).slice(0,5);return `<div class="page"><div class="stats">${statCard("CATALOG ITEMS",s.total,"Distinct part types","⚙")}${statCard("TOTAL UNITS",s.units,"Available units","▤")}${statCard("LOW / OUT OF STOCK",s.low+s.out,"Items needing attention","⚠")}${statCard("STOCK VALUE",money(s.value),"Quantity × unit price","₱")}</div><div class="content-grid"><div class="panel"><div class="section-head"><h3>Current inventory valuation</h3><button class="primary" id="exportProducts">Export CSV</button></div><p class="panel-sub">Estimated value of available stock by motorcycle part</p>${top.map(p=>`<div class="report-card"><div><b>${esc(p.name)}</b><small>${p.quantity} units × ${money(p.price)}</small></div><b>${money(p.quantity*p.price)}</b></div>`).join("")||`<div class="empty">No products to report.</div>`}</div><div class="panel"><div class="section-head"><h3>Inventory health</h3></div>${statBar("In stock",state.products.filter(p=>stockStatus(p)[1]==="ok").length,s.total)}${statBar("Low stock",s.low,s.total)}${statBar("Out of stock",s.out,s.total)}<hr style="border:0;border-top:1px solid var(--line);margin:20px 0"><button class="secondary full" id="exportMovements">Export movement history (CSV)</button><p class="hint">Reports reflect the records currently stored in this browser. For multi-device use, connect Firebase as described in README.</p></div></div></div>`}
function pricesPage(){
 const rows=state.products.map(p=>`<tr><td><div class="part-cell"><div class="part-icon">⚙</div><div><b>${esc(p.name)}</b><small>${esc(p.sku)}</small></div></div></td><td>${esc(p.category)}</td><td>${p.quantity}</td><td><b>${money(p.price)}</b></td><td><button class="secondary" data-price="${esc(p.id)}">Change price</button></td></tr>`).join("");
 return `<div class="page"><div class="panel"><div class="section-head"><div><h3>Product prices</h3><p class="panel-sub">Update selling prices without changing stock or product details.</p></div><span class="pill ok">${state.products.length} items</span></div><div class="table-wrap"><table><thead><tr><th>Part / SKU</th><th>Category</th><th>Qty</th><th>Current unit price</th><th>Action</th></tr></thead><tbody>${rows||`<tr><td colspan="5"><div class="empty">No products found. Add products from Inventory.</div></td></tr>`}</tbody></table></div></div></div>`;
}
function analyticsPage(){
 const sales=state.movements.filter(m=>m.type==="OUT"&&(m.saleTotal!==undefined||String(m.note||"").startsWith("POS Sale")));
 const productById=new Map(state.products.map(p=>[p.id,p]));
 const revenueFor=m=>Number(m.saleTotal??((Number(m.unitPrice)||Number(productById.get(m.productId)?.price)||0)*Number(m.quantity||0)));
 const now=new Date(),days=[];
 for(let offset=6;offset>=0;offset--){
   const date=new Date(now.getFullYear(),now.getMonth(),now.getDate()-offset);
   const key=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}`;
   days.push({key,label:date.toLocaleDateString("en-PH",{weekday:"short"}),revenue:0});
 }
 const daily=new Map(days.map(day=>[day.key,day]));
 const productsSold=new Map(),saleIds=new Set();
 let weeklyRevenue=0,weeklyUnits=0,totalRevenue=0;
 sales.forEach(m=>{
   const revenue=revenueFor(m),quantity=Number(m.quantity)||0,parsedDate=new Date(m.date);
   totalRevenue+=revenue;
   saleIds.add(m.saleId||m.id);
   productsSold.set(m.productId,{name:m.productName||productById.get(m.productId)?.name||"Unknown part",quantity:(productsSold.get(m.productId)?.quantity||0)+quantity,revenue:(productsSold.get(m.productId)?.revenue||0)+revenue});
   if(!Number.isNaN(parsedDate.getTime())){
     const key=`${parsedDate.getFullYear()}-${String(parsedDate.getMonth()+1).padStart(2,"0")}-${String(parsedDate.getDate()).padStart(2,"0")}`;
     if(daily.has(key)){daily.get(key).revenue+=revenue;weeklyRevenue+=revenue;weeklyUnits+=quantity}
   }
 });
 const bestSelling=[...productsSold.values()].sort((a,b)=>b.quantity-a.quantity).slice(0,5);
 const maxDaily=Math.max(...days.map(day=>day.revenue),1);
 return `<div class="page"><div class="stats">${statCard("SALES (7 DAYS)",money(weeklyRevenue),"Recorded POS revenue","₱")}${statCard("UNITS SOLD (7 DAYS)",weeklyUnits,"From completed POS sales","▤")}${statCard("POS TRANSACTIONS",saleIds.size,"Completed customer sales","🛒")}${statCard("ALL-TIME POS REVENUE",money(totalRevenue),"Available movement history","↗")}</div><div class="content-grid"><div class="panel"><h3>Sales trend</h3><p class="panel-sub">Recorded point-of-sale revenue over the last seven days</p><div class="analytics-chart">${days.map(day=>`<div class="analytics-chart-column"><b>${compactMoney(day.revenue)}</b><div class="analytics-chart-track"><div class="analytics-chart-fill" style="height:${Math.round(day.revenue/maxDaily*100)}%"></div></div><small>${day.label}</small></div>`).join("")}</div></div><div class="panel"><h3>Top-selling parts</h3><p class="panel-sub">Ranked by units sold through POS</p>${bestSelling.map((item,index)=>`<div class="report-card"><div><b>${index+1}. ${esc(item.name)}</b><small>${item.quantity} unit(s) sold</small></div><b>${money(item.revenue)}</b></div>`).join("")||`<div class="empty">No POS sales recorded yet.</div>`}</div></div><p class="hint">Sales analytics include completed POS sales only. Older POS records without a saved sale price use the current product price as an estimate.</p></div>`;
}
function statBar(label,n,total){const pct=total?Math.round(n/total*100):0;return `<div style="margin:16px 0"><div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:8px"><span>${label}</span><b>${n} <span class="muted">(${pct}%)</span></b></div><div style="height:7px;background:#edf2ee;border-radius:8px;overflow:hidden"><div style="height:100%;width:${pct}%;background:${label==="Out of stock"?"#c84b51":label==="Low stock"?"#d6a03d":"#26885f"};border-radius:8px"></div></div></div>`}
function usersPage(){return `<div class="page"><div class="panel"><div class="section-head"><div><h3>System users</h3><p class="panel-sub">Role-based access to inventory operations</p></div><button class="primary" id="addUser">＋ Add user</button></div><div class="table-wrap"><table><thead><tr><th>User</th><th>Email</th><th>Role</th><th>Actions</th></tr></thead><tbody>${state.users.map(u=>`<tr><td><div class="user-row"><div class="avatar">${esc(u.name[0].toUpperCase())}</div><b>${esc(u.name)}</b></div></td><td>${esc(u.email)}</td><td><span class="pill ${u.role==="Administrator"?"ok":"low"}">${esc(u.role)}</span></td><td>${u.id!==currentUser.id?`<button class="danger" data-remove-user="${esc(u.id)}">Remove</button>`:"<span class='muted'>Current user</span>"}</td></tr>`).join("")}</tbody></table></div><p class="hint">Demo users are local browser accounts, not production Firebase Authentication accounts.</p></div></div>`}
function wirePage(){
if ($("#addToCartBtn")) $("#addToCartBtn").onclick = addToCart;
if ($("#checkoutBtn")) $("#checkoutBtn").onclick = processCheckout;
$$("[data-price]").forEach(button=>button.onclick=()=>openPriceModal(button.dataset.price));
$$("[data-cart-index]").forEach(input=>input.onchange=()=>updateCartQty(Number(input.dataset.cartIndex),input.value));
$$("[data-remove-cart]").forEach(button=>button.onclick=()=>removeFromCart(Number(button.dataset.removeCart)));
 $$("[data-goto]").forEach(b=>b.onclick=()=>navigate(b.dataset.goto));
 if($("#stickerSearch"))wireStickerPage();
 $$("[data-edit]").forEach(b=>b.onclick=()=>openProductModal(b.dataset.edit));
 $$("[data-delete]").forEach(b=>b.onclick=()=>deleteProduct(b.dataset.delete));
 if($("#productSearch")){const filter=()=>{const q=$("#productSearch").value.toLowerCase(),f=$("#stockFilter").value;const list=state.products.filter(p=>(`${p.name} ${p.sku} ${p.category}`.toLowerCase().includes(q))&&(!f||stockStatus(p)[1]===f));$("#productTable").innerHTML=productRows(list)};$("#productSearch").oninput=filter;$("#stockFilter").onchange=filter}
 if($("#newMovement"))$("#newMovement").onclick=()=>openMovementModal();
 if($("#attendanceAction"))$("#attendanceAction").onclick=()=>punchAttendance($("#attendanceAction").dataset.action);
 if($("#movementSearch")){const filter=()=>{const q=$("#movementSearch").value.toLowerCase(),f=$("#movementFilter").value;$("#movementTable").innerHTML=movementRows(state.movements.filter(m=>(`${m.productName} ${m.user} ${m.id}`.toLowerCase().includes(q))&&(!f||m.type===f)))};$("#movementSearch").oninput=filter;$("#movementFilter").onchange=filter}
 if($("#exportProducts"))$("#exportProducts").onclick=()=>downloadCSV("xavery_inventory.csv",["SKU","Name","Category","Quantity","Minimum Stock","Unit Price PHP","Location","Status"],state.products.map(p=>[p.sku,p.name,p.category,p.quantity,p.minStock,p.price,p.location,stockStatus(p)[0]]));
 if($("#exportMovements"))$("#exportMovements").onclick=()=>downloadCSV("xavery_stock_movements.csv",["ID","SKU","Part","Type","Quantity","Date","User","Note"],state.movements.map(m=>[m.id,m.productId,m.productName,m.type,m.quantity,m.date,m.user,m.note]));
 if($("#addUser"))$("#addUser").onclick=openUserModal;
 $$("[data-assign-role]").forEach(button=>button.onclick=()=>assignUserRole(button.dataset.assignRole,button.dataset.role));
 $$("[data-remove-user]").forEach(b=>b.onclick=()=>{if(confirm("Remove this demo user?")){state.users=state.users.filter(u=>u.id!==b.dataset.removeUser);saveDB();renderPage();toast("User removed.")}});
 if($("#startCamera"))$("#startCamera").onclick=startCamera;
 if($("#stopCameraBtn"))$("#stopCameraBtn").onclick=stopCamera;
 if($("#captureBtn"))$("#captureBtn").onclick=captureFrame;
 if($("#uploadImage"))$("#uploadImage").onclick=()=>$("#imageUpload").click();
 if($("#imageUpload"))$("#imageUpload").onchange=async e=>{const f=e.target.files[0];if(f)await sendImageForDetection(f)};
}
function openModal(title,body,onSubmit,submitText="Save"){const root=$("#modalRoot");root.innerHTML=`<div class="modal-backdrop"><form class="modal" id="activeModal"><div class="modal-head"><h3>${title}</h3><button type="button" class="modal-close" id="closeModal">✕</button></div>${body}<div class="modal-actions"><button type="button" class="secondary" id="cancelModal">Cancel</button><button type="submit" class="primary">${submitText}</button></div></form></div>`;$("#closeModal").onclick=$("#cancelModal").onclick=()=>root.innerHTML="";const form=$("#activeModal"),submitButton=form.querySelector('button[type="submit"]');form.onsubmit=async e=>{e.preventDefault();if(submitButton.disabled)return;submitButton.disabled=true;try{if(await onSubmit()!==false)root.innerHTML=""}catch(error){console.error("Form submission failed.",error);toast(error.message||"Unable to save.")}finally{if(form.isConnected)submitButton.disabled=false}};root.querySelector(".modal-backdrop").onclick=e=>{if(e.target.classList.contains("modal-backdrop"))root.innerHTML=""}}
function openProductModal(id){
 if(!canEdit()){toast("Only administrators can edit catalog details.");return}
 const p=state.products.find(x=>x.id===id)||{id:"",name:"",category:"Engine",sku:"",quantity:0,minStock:3,price:0,location:"",description:""};
 const isEdit=!!id;
 let removeExistingPhoto=false;
 let previewUrl="";
 const releasePreview=()=>{if(previewUrl){URL.revokeObjectURL(previewUrl);previewUrl=""}};
 openModal(isEdit?"Edit motorcycle part":"Add motorcycle part",`<div class="form-grid"><label>Part name<input name="name" required value="${esc(p.name)}"></label><label>SKU / item code<input name="sku" required value="${esc(p.sku)}"></label><label>Category<select name="category">${["Engine","Engine & Exhaust","Suspension","Braking","Controls","Body & Accessories","Electrical","Tires & Wheels","Other"].map(c=>`<option ${p.category===c?"selected":""}>${c}</option>`).join("")}</select></label><label>Quantity in stock<input name="quantity" type="number" min="0" required value="${p.quantity}"></label><label>Minimum stock alert<input name="minStock" type="number" min="0" required value="${p.minStock}"></label><label>Unit price (PHP)<input name="price" type="number" min="0" step=".01" required value="${p.price}"></label><label class="wide">Storage location<input name="location" value="${esc(p.location)}" placeholder="e.g. Rack A1"></label><div class="wide"><label for="productPhotoInput">Item photo</label><div class="product-photo-field"><div class="product-photo-preview" id="productPhotoPreview">${p.imageUrl?`<img src="${esc(p.imageUrl)}" alt="Current photo of ${esc(p.name)}">`:`<span>No photo selected</span>`}</div><div class="product-photo-controls"><input id="productPhotoInput" type="file" accept="image/*"><small>Choose an image up to 15 MB.</small><button type="button" class="secondary ${p.imageUrl?"":"hidden"}" id="removeProductPhoto">Remove photo</button></div></div></div><label class="wide">Description<textarea name="description" rows="2">${esc(p.description)}</textarea></label></div>`,async()=>{
  const form=$("#activeModal"),fields=new FormData(form),sku=String(fields.get("sku")).trim(),photoInput=$("#productPhotoInput"),photo=photoInput.files[0];
  if(state.products.some(x=>x.sku.toLowerCase()===sku.toLowerCase()&&x.id!==p.id)){toast("That SKU already exists.");return false}
  if(photo&&(!photo.type.startsWith("image/")||photo.size>15*1024*1024)){toast("Choose an image smaller than 15 MB.");return false}
  const old=state.products.find(x=>x.id===id),productId=p.id||nextId("P");
  let uploadedPhoto=null;
  if(photo)uploadedPhoto=await uploadProductPhoto(productId,photo);
  const obj={...p,id:productId,name:String(fields.get("name")).trim(),sku,category:fields.get("category"),quantity:Number(fields.get("quantity")),minStock:Number(fields.get("minStock")),price:Number(fields.get("price")),location:String(fields.get("location")).trim(),description:String(fields.get("description")).trim(),imageUrl:uploadedPhoto?.imageUrl||(!removeExistingPhoto?p.imageUrl||null:null),imagePath:uploadedPhoto?.imagePath||(!removeExistingPhoto?p.imagePath||null:null)};
  const previousProducts=state.products.slice(),previousMovements=state.movements.slice();
  if(isEdit){const delta=obj.quantity-old.quantity;if(delta)recordMovement(obj,delta>0?"IN":"OUT",Math.abs(delta),"Quantity adjusted while editing product");state.products=state.products.map(x=>x.id===id?obj:x)}
  else{state.products.unshift(obj);if(obj.quantity)recordMovement(obj,"IN",obj.quantity,"Opening stock")}
  if(!await saveDB()){
   state.products=previousProducts;state.movements=previousMovements;
   if(uploadedPhoto&&!await removeProductPhoto(uploadedPhoto.imagePath))toast("Part was not saved, and the uploaded photo could not be removed.");
   return false;
  }
  const oldPhotoRemoved=old?.imagePath&&(removeExistingPhoto||uploadedPhoto)&&old.imagePath!==obj.imagePath
   ?await removeProductPhoto(old.imagePath)
   :true;
  releasePreview();
  renderPage();toast(oldPhotoRemoved?(isEdit?"Part updated.":"Part added."):"Part saved, but its previous photo could not be removed.");return true
 });
 const photoInput=$("#productPhotoInput"),preview=$("#productPhotoPreview");
 $("#closeModal").addEventListener("click",releasePreview);
 $("#cancelModal").addEventListener("click",releasePreview);
 $(".modal-backdrop").addEventListener("click",event=>{if(event.target.classList.contains("modal-backdrop"))releasePreview()});
 photoInput.onchange=()=>{
  const file=photoInput.files[0];
  if(!file)return;
  if(!["image/jpeg","image/png","image/webp","image/gif"].includes(file.type)||file.size>15*1024*1024){photoInput.value="";toast("Choose a JPG, PNG, WEBP, or GIF image smaller than 15 MB.");return}
  removeExistingPhoto=false;
  const image=document.createElement("img");
  image.alt="Selected part photo";
  releasePreview();
  previewUrl=URL.createObjectURL(file);
  image.src=previewUrl;
  preview.replaceChildren(image);
  $("#removeProductPhoto").classList.remove("hidden");
 };
 $("#removeProductPhoto").onclick=()=>{
  photoInput.value="";
  releasePreview();
  removeExistingPhoto=true;
  preview.innerHTML="<span>No photo selected</span>";
  $("#removeProductPhoto").classList.add("hidden");
 };
}
async function uploadProductPhoto(productId,file){
 if(!firebaseStorage)throw new Error("Firebase Storage is unavailable. Check the Storage SDK and Firebase setup.");
 const extension={"image/jpeg":"jpg","image/png":"png","image/webp":"webp","image/gif":"gif"}[file.type];
 if(!extension)throw new Error("Use a JPG, PNG, WEBP, or GIF image.");
 const imagePath=`product-images/${productId}/${Date.now()}.${extension}`;
 let uploadedReference=null;
 try{
  const snapshot=await firebaseStorage.ref(imagePath).put(file,{contentType:file.type});
  uploadedReference=snapshot.ref;
  return {imageUrl:await uploadedReference.getDownloadURL(),imagePath};
 }catch(error){
  console.error("Product photo upload failed.",error);
  if(uploadedReference)await removeProductPhoto(imagePath);
  throw new Error(`Photo upload failed: ${error.message}`);
 }
}
async function removeProductPhoto(imagePath){
 if(!firebaseStorage||!imagePath)return false;
 try{await firebaseStorage.ref(imagePath).delete();return true}
 catch(error){
  console.error("Product photo cleanup failed.",error);
  return false;
 }
}
function openPriceModal(id){
 if(!canEdit()){toast("Only administrators can change prices.");return}
 const product=state.products.find(item=>item.id===id);
 if(!product)return;
 openModal(`Change price · ${esc(product.name)}`,`<p class="panel-sub">Current price: ${money(product.price)}</p><label>New unit price (PHP)<input name="price" type="number" min="0" step=".01" required value="${Number(product.price)||0}"></label>`,()=>{
   const value=Number(new FormData($("#activeModal")).get("price"));
   if(!Number.isFinite(value)||value<0){toast("Enter a valid non-negative price.");return false}
   product.price=value;
   saveDB();renderPage();toast("Product price updated.");return true;
 });
}
function openMovementModal(){const opts=state.products.map(p=>`<option value="${esc(p.id)}">${esc(p.name)} (${p.quantity} in stock)</option>`).join("");if(!opts){toast("Add a part before recording movements.");return}openModal("Record stock movement",`<label>Motorcycle part<select name="productId" required>${opts}</select></label><div class="form-grid"><label>Movement type<select name="type"><option value="IN">Stock in (add)</option><option value="OUT">Stock out (remove)</option></select></label><label>Quantity<input name="quantity" type="number" min="1" step="1" value="1" required></label><label class="wide">Note / reference<input name="note" placeholder="e.g. supplier delivery or shop usage"></label></div>`,()=>{const f=new FormData($("#activeModal")),p=state.products.find(x=>x.id===f.get("productId")),type=f.get("type"),qty=Number(f.get("quantity"));if(!p||qty<1||!Number.isInteger(qty)){toast("Enter a valid quantity.");return false}if(type==="OUT"&&qty>p.quantity){toast("Stock-out quantity exceeds available stock.");return false}p.quantity+=type==="IN"?qty:-qty;recordMovement(p,type,qty,String(f.get("note")).trim());saveDB();renderPage();toast("Stock movement recorded.");return true})}
function recordMovement(p,type,quantity,note,saleTotal,saleId){state.movements.push({id:nextId("M"),productId:p.id,productName:p.name,type,quantity,date:new Date().toISOString(),user:currentUser?.name||"System",note,unitPrice:Number(p.price)||0,...(saleTotal===undefined?{}:{saleTotal}),...(saleId?{saleId}:{})})}
function nextId(prefix){return prefix+"-"+Date.now().toString().slice(-8)+Math.random().toString(36).slice(2,4).toUpperCase()}
function deleteProduct(id){if(!canEdit())return;const p=state.products.find(x=>x.id===id);if(!p)return;if(!confirm(`Delete "${p.name}" from the catalog? Existing movement history will remain.`))return;state.products=state.products.filter(x=>x.id!==id);saveDB();renderPage();toast("Part deleted.")}
function openUserModal(){openModal("Add demo user",`<label>Full name<input name="name" required></label><label>Email address<input name="email" type="email" required></label><label>Temporary password<input name="password" type="password" minlength="6" required></label><label>Role<select name="role"><option>Inventory Clerk</option><option>Administrator</option></select></label><p class="hint">Demo users are stored in this browser only. Use Firebase Authentication before real deployment.</p>`,()=>{const f=new FormData($("#activeModal")),email=String(f.get("email")).trim().toLowerCase();if(state.users.some(u=>u.email.toLowerCase()===email)){toast("Email already exists.");return false}state.users.push({id:nextId("U"),name:String(f.get("name")).trim(),email,role:f.get("role"),password:String(f.get("password"))});saveDB();renderPage();toast("Demo user added.");return true})}
async function startCamera(){try{if(!navigator.mediaDevices?.getUserMedia)throw Error("Camera API unavailable. Use localhost or HTTPS.");stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:"environment"}},audio:false});const box=$("#cameraBox");box.innerHTML="<video id='cameraVideo' autoplay playsinline></video>";$("#cameraVideo").srcObject=stream;$("#captureBtn").disabled=false;$("#stopCameraBtn").disabled=false;$("#startCamera").disabled=true}catch(e){toast(e.message||"Unable to open camera.")}}
function stopCamera(){if(stream){stream.getTracks().forEach(t=>t.stop());stream=null}const video=$("#cameraVideo");if(video){const box=$("#cameraBox");box.innerHTML=`<div class="camera-placeholder"><div class="camera-icon">◎</div><b>Camera is not active</b><p>Start the camera to scan a part.</p></div>`;if($("#captureBtn"))$("#captureBtn").disabled=true;if($("#stopCameraBtn"))$("#stopCameraBtn").disabled=true;if($("#startCamera"))$("#startCamera").disabled=false}}
function captureFrame(){const v=$("#cameraVideo"),c=$("#captureCanvas");if(!v||!v.videoWidth){toast("Camera is not ready.");return}c.width=v.videoWidth;c.height=v.videoHeight;c.getContext("2d").drawImage(v,0,0);c.toBlob(blob=>sendImageForDetection(blob),"image/jpeg",.9)}
async function sendImageForDetection(file){
 const result=$("#detectionResult"),actions=$("#scanActions");
 result.innerHTML="<b>Processing image…</b><p>Checking the optional YOLOv8 detection service.</p>";actions.innerHTML="";
 try{
   const formData=new FormData();formData.append("file",file,"scan.jpg");
   const response=await fetch("http://127.0.0.1:8000/detect",{method:"POST",body:formData});
   if(!response.ok)throw Error("Detection API returned an error");
   const data=await response.json();
   if(!data.detections?.length){result.innerHTML="<b>No supported part detected</b><p>Try better lighting, a clearer view, or confirm the part manually.</p>";return}
   const best=data.detections.sort((a,b)=>b.confidence-a.confidence)[0];lastDetected=best;
   const forSale=currentPage==="pos";
   result.innerHTML=`<b>${esc(best.class_name)}</b><p>Confidence: ${(best.confidence*100).toFixed(1)}%<br>Detected objects: ${data.detections.length}<br>${forSale?"Choose a matching catalog item and add it to the sale.":"Choose a matching catalog item below to record the movement."}</p>`;
   actions.innerHTML=`<label>Match catalog part<select id="detectedProduct">${state.products.map(p=>`<option value="${esc(p.id)}" ${p.name.toLowerCase()===best.class_name.toLowerCase()?"selected":""}>${esc(p.name)} (${p.quantity} available)</option>`).join("")}</select></label>${forSale?"":`<label>Movement<select id="detectedType"><option value="IN">Stock in</option><option value="OUT">Stock out</option></select></label>`}<label>Quantity<input id="detectedQty" type="number" min="1" step="1" value="${Math.max(1,data.detections.length)}"></label><button id="confirmDetection" class="primary">${forSale?"Add to sale":"Confirm and update stock"}</button>`;
   $("#confirmDetection").onclick=()=>{
     const product=state.products.find(item=>item.id===$("#detectedProduct").value),quantity=Number($("#detectedQty").value);
     if(!product||!Number.isInteger(quantity)||quantity<1){toast("Choose a part and valid quantity.");return}
     if(forSale){
       stopCamera();
       if(!addProductToCart(product.id,quantity,false))return;
       result.innerHTML=`<b>Added to sale</b><p>${esc(product.name)} × ${quantity} added to the POS cart. Complete the sale to update stock.</p>`;
       renderPage();
     }else{
       const type=$("#detectedType").value;
       if(type==="OUT"&&quantity>product.quantity){toast("Stock-out quantity exceeds available stock.");return}
       product.quantity+=type==="IN"?quantity:-quantity;
       recordMovement(product,type,quantity,`Camera scan (${(best.confidence*100).toFixed(1)}% confidence)`);
       saveDB();result.innerHTML=`<b>Inventory updated</b><p>${esc(product.name)}: ${type==="IN"?"added":"removed"} ${quantity} unit(s). New stock: ${product.quantity}.</p>`;
       toast("Detection confirmed and stock updated.");
     }
     actions.innerHTML="";
   };
 }catch(error){
   result.innerHTML=`<b>Image captured, but no detection service is available.</b><p>${esc(error.message)}<br><br>For real YOLOv8 detection, start the optional API in the vision_api folder and provide your trained <code>best.pt</code> model.</p>`;
 }
}
function downloadCSV(filename,headers,rows){const csv=[headers,...rows].map(row=>row.map(v=>`"${String(v??"").replace(/"/g,'""')}"`).join(",")).join("\r\n");const blob=new Blob(["\ufeff"+csv],{type:"text/csv;charset=utf-8;"}),url=URL.createObjectURL(blob),a=document.createElement("a");a.href=url;a.download=filename;a.click();URL.revokeObjectURL(url);toast("CSV export prepared.")}
let firebaseAuth, firebaseDb, firebaseRtdb, firebaseStorage, firebaseUnsubscribers = [], firebaseBaseline = null, connectedUid = null, attendanceActionInProgress = false;
let registrationInProgress = false;
let authActionInProgress = false;
let profileSyncWarning = "";

function firebaseConfigIsComplete(config) {
  return config && config.apiKey && config.authDomain && config.projectId && config.appId;
}

function stopFirestoreSubscriptions() {
  firebaseUnsubscribers.forEach(unsubscribe => unsubscribe());
  firebaseUnsubscribers = [];
  firebaseBaseline = null;
  connectedUid = null;
  attendanceSyncError = "";
}

function serializeFirestoreItem(item) {
  const { id, ...data } = item;
  return data;
}

async function saveDB() {
  if (!firebaseDb || !firebaseBaseline || !currentUser) {
    toast("Your changes could not be saved because Firebase is not connected.");
    return false;
  }

  const operations = [];
  const productReads = [];
  for (const collectionName of ["products", "movements", "users"]) {
    const previous = new Map(firebaseBaseline[collectionName].map(item => [item.id, item]));
    const current = new Map(state[collectionName].map(item => [item.id, item]));

    for (const [id, item] of current) {
      const oldItem = previous.get(id);
      const reference = firebaseDb.collection(collectionName).doc(id);
      if (!oldItem) {
        operations.push({ type: "set", reference, data: serializeFirestoreItem(item) });
      } else {
        const changedFields = {};
        for (const key of new Set([...Object.keys(oldItem), ...Object.keys(item)])) {
          if (key !== "id" && JSON.stringify(oldItem[key]) !== JSON.stringify(item[key])) {
            changedFields[key] = item[key];
          }
        }
        if (Object.keys(changedFields).length) {
          operations.push({ type: "update", reference, data: changedFields });
          if (collectionName === "products" && Object.hasOwn(changedFields, "quantity")) {
            productReads.push({ reference, delta: item.quantity - oldItem.quantity });
          }
        }
      }
    }
    for (const id of previous.keys()) {
      if (!current.has(id)) {
        operations.push({ type: "delete", reference: firebaseDb.collection(collectionName).doc(id) });
      }
    }
  }

  if (!operations.length) return true;
  try {
    await firebaseDb.runTransaction(async transaction => {
      const productSnapshots = await Promise.all(
        productReads.map(({ reference }) => transaction.get(reference))
      );
      const productQuantities = new Map(
        productSnapshots.map((snapshot, index) => [productReads[index].reference.path, snapshot])
      );

      for (const operation of operations) {
        if (operation.type === "delete") {
          transaction.delete(operation.reference);
        } else if (operation.type === "set") {
          transaction.set(operation.reference, operation.data);
        } else {
          const data = { ...operation.data };
          const productRead = productReads.find(({ reference }) => reference.path === operation.reference.path);
          if (productRead) {
            const snapshot = productQuantities.get(operation.reference.path);
            if (!snapshot?.exists) throw new Error("A product changed or was removed in another session. Refresh and try again.");
            data.quantity = snapshot.data().quantity + productRead.delta;
            if (data.quantity < 0) throw new Error("There is not enough stock for this change. Refresh and try again.");
          }
          transaction.update(operation.reference, data);
        }
      }
    });
    return true;
  } catch (error) {
    console.error("Unable to save inventory changes to Firestore.", error);
    toast(`Save failed: ${error.message}`);
    return false;
  }
}

async function punchAttendance(action) {
  if (attendanceActionInProgress || !firebaseDb || !currentUser) return;
  attendanceActionInProgress = true;
  const recordDate = dateKey();
  const recordId = `${currentUser.id}_${recordDate}`;
  const recordReference = firebaseDb.collection("attendance").doc(
    action === "out"
      ? state.attendance.find(record => record.employeeId === currentUser.id && !record.timeOut)?.id || recordId
      : recordId
  );
  renderPage();
  try {
    await firebaseDb.runTransaction(async transaction => {
      const snapshot = await transaction.get(recordReference);
      if (action === "in") {
        if (snapshot.exists) {
          throw new Error("You already have an attendance record for today.");
        }
        transaction.set(recordReference, {
          employeeId: currentUser.id,
          employeeName: currentUser.name,
          date: recordDate,
          timeIn: firebase.firestore.FieldValue.serverTimestamp(),
          timeOut: null
        });
        return;
      }
      if (!snapshot.exists || snapshot.get("employeeId") !== currentUser.id) {
        throw new Error("Your active time-in record could not be found. Refresh and try again.");
      }
      if (snapshot.get("timeOut")) {
        throw new Error("You have already clocked out.");
      }
      transaction.update(recordReference, { timeOut: firebase.firestore.FieldValue.serverTimestamp() });
    });
    toast(action === "in" ? "Time in recorded." : "Time out recorded.");
  } catch (error) {
    console.error(`Employee time ${action} could not be recorded.`, error);
    toast(`Time ${action} failed: ${error.message}`);
  } finally {
    attendanceActionInProgress = false;
    renderPage();
  }
}

function setFirebaseLoginError(message, success = false) {
  const error = $("#loginError");
  error.textContent = message;
  error.classList.toggle("success", success);
}

function clearLoginCredentials() {
  $("#loginEmail").value = "";
  $("#loginPassword").value = "";
}

function openRegistrationModal() {
  openModal(
    "Create account",
    `<label>Full name<input name="name" autocomplete="name" required></label>
     <label>Email address<input name="email" type="email" autocomplete="email" required></label>
     <label>Password<input name="password" type="password" autocomplete="new-password" minlength="6" required></label>
     <label>Confirm password<input name="confirmPassword" type="password" autocomplete="new-password" minlength="6" required></label>
     <p class="hint">New accounts are created as Sales Staff. Company Administrator access can only be assigned by the Project Administrator.</p>`,
    () => {
      const form = new FormData($("#activeModal"));
      const name = String(form.get("name")).trim();
      const email = String(form.get("email")).trim();
      const password = String(form.get("password"));
      if (password !== form.get("confirmPassword")) {
        toast("Passwords do not match.");
        return false;
      }
      if (!name) {
        toast("Enter your full name.");
        return false;
      }
      registerStaffAccount(name, email, password);
      return false;
    },
    "Register"
  );
}

async function registerStaffAccount(name, email, password) {
  const submitButton = $("#activeModal button[type='submit']");
  if (registrationInProgress) return;
  registrationInProgress = true;
  submitButton.disabled = true;
  submitButton.textContent = "Creating account…";

  let credential;
  try {
    credential = await firebaseAuth.createUserWithEmailAndPassword(email, password);
    try {
      await firebaseDb.collection("users").doc(credential.user.uid).set({
        name,
        email: credential.user.email,
        role: "Staff"
      });
    } catch (profileError) {
      try {
        await credential.user.delete();
      } catch (cleanupError) {
        console.error("Could not remove an account after its staff profile failed to save.", cleanupError);
        await firebaseAuth.signOut();
        throw new Error(`Account was created, but its staff profile could not be saved or rolled back: ${profileError.message}`);
      }
      throw profileError;
    }

    try {
      await credential.user.sendEmailVerification();
    } catch (verificationError) {
      throw new Error(`Account created, but the verification email could not be sent: ${verificationError.message}. Use Sign in to try sending it again.`);
    }

    $("#modalRoot").innerHTML = "";
    $("#loginEmail").value = credential.user.email || email;
    $("#loginPassword").value = "";
    setFirebaseLoginError("Account created. Verify your email using the link we sent before signing in.", true);
    await firebaseAuth.signOut();
  } catch (error) {
    console.error("Staff registration failed.", error);
    if (credential) {
      stopFirestoreSubscriptions();
      currentUser = null;
      $("#app").classList.add("hidden");
      $("#loginScreen").classList.remove("hidden");
      try {
        await firebaseAuth.signOut();
      } catch (signOutError) {
        console.error("Could not sign out after staff registration failed.", signOutError);
      }
    }
    setFirebaseLoginError(error.message);
    const activeSubmitButton = $("#activeModal button[type='submit']");
    if (activeSubmitButton) {
      activeSubmitButton.disabled = false;
      activeSubmitButton.textContent = "Register";
    }
  } finally {
    registrationInProgress = false;
  }
}

async function connectFirebaseUser(user) {
  if (connectedUid === user.uid) return;
  stopFirestoreSubscriptions();
  let profile = null;
  let role = null;
  const firestoreProfile = await firebaseDb.collection("users").doc(user.uid).get();
  if (firestoreProfile.exists) {
    profile = firestoreProfile.data();
    role = profile.role;
  } else {
    const realtimeProfile = await firebaseRtdb.ref(`users/${user.uid}`).once("value");
    if (realtimeProfile.exists()) {
      profile = realtimeProfile.val();
      role = "Staff";
      try {
        await firebaseDb.collection("users").doc(user.uid).set({
          name: profile.name || user.email,
          email: user.email,
          role
        });
      } catch (error) {
        console.error("Could not mirror the Android profile to Firestore.", error);
        profileSyncWarning = "Signed in, but your profile could not be synced to the web user list. Check Firestore rules.";
      }
    }
  }
  if (!profile) {
    throw new Error("Your Firebase account has no profile in Firestore or Realtime Database. Ask an administrator to check your account.");
  }

  role = normalizeRole(role);
  if (!["Project Administrator", "Administrator", "Staff", "Sales Staff"].includes(role)) {
    throw new Error("Your user profile has an invalid role. Ask an administrator to check it.");
  }

  currentUser = {
    id: user.uid,
    name: profile.name || user.email,
    email: user.email,
    role
  };
  const syncWarning = profileSyncWarning;
  profileSyncWarning = "";
  connectedUid = user.uid;
  attendanceSyncError = "";
  firebaseBaseline = { products: [], movements: [], users: [], attendance: [] };
  state = { products: [], movements: [], users: [], attendance: [] };
  let firstSnapshotCount = 0;
  const expectedSnapshots = 3;
  let appShown = false;

  const subscribe = (collectionName, query = firebaseDb.collection(collectionName), optional = false) => new Promise((resolve, reject) => {
    let firstSnapshot = true;
    const unsubscribe = query.onSnapshot({ includeMetadataChanges: true }, snapshot => {
      const items = snapshot.docs.map(doc => {
        const item = { id: doc.id, ...doc.data() };
        return collectionName === "users" ? { ...item, role: normalizeRole(item.role) } : item;
      });
      if (collectionName === "attendance") attendanceSyncError = "";
      state[collectionName] = items;
      firebaseBaseline[collectionName] = structuredClone(items);
      if (firstSnapshot && !snapshot.metadata.fromCache) {
        firstSnapshot = false;
        firstSnapshotCount++;
        if (firstSnapshotCount === expectedSnapshots && !appShown) {
          appShown = true;
          $("#loginError").textContent = "";
          showApp();
          if (attendanceSyncError) toast(attendanceSyncError);
          if (syncWarning) toast(syncWarning);
        }
        resolve();
      } else if (!firstSnapshot && currentUser && !snapshot.metadata.fromCache) {
        renderPage();
      }
    }, error => {
      if (firstSnapshot) {
        if (optional) {
          firstSnapshot = false;
          attendanceSyncError = "Ask an administrator to publish the latest Firestore rules, then refresh to use the Time Clock.";
          state.attendance = [];
          firebaseBaseline.attendance = [];
          firstSnapshotCount++;
          console.error("Could not load the employee attendance records.", error);
          if (firstSnapshotCount === expectedSnapshots && !appShown) {
            appShown = true;
            $("#loginError").textContent = "";
            showApp();
            toast(attendanceSyncError);
            if (syncWarning) toast(syncWarning);
          }
          resolve();
          return;
        }
        reject(new Error(`Could not load Firestore "${collectionName}": ${error.message}`));
      }
      else {
        if (optional) {
          attendanceSyncError = "Time Clock data is unavailable. Ask an administrator to check and publish the Firestore rules.";
          console.error("Employee attendance updates failed.", error);
          toast(attendanceSyncError);
          if (currentPage === "attendance") renderPage();
          return;
        }
        console.error(`Firestore subscription failed for ${collectionName}.`, error);
        toast(`Live updates stopped: ${error.message}`);
      }
    });
    firebaseUnsubscribers.push(unsubscribe);
  });

  if (canEdit()) {
    state.users = [];
    await Promise.all([subscribe("products"), subscribe("movements"), subscribe("users")]);
  } else {
    state.users = [currentUser];
    firebaseBaseline.users = [currentUser];
    await Promise.all([
      subscribe("products"),
      subscribe("movements"),
      subscribe("attendance", firebaseDb.collection("attendance").where("employeeId", "==", user.uid), true)
    ]);
  }

  const profileUnsubscribe = firebaseDb.collection("users").doc(user.uid).onSnapshot(profileSnapshot => {
    if (!profileSnapshot.exists || !currentUser || connectedUid !== user.uid) return;
    const updatedRole = normalizeRole(profileSnapshot.get("role"));
    if (!["Project Administrator", "Administrator", "Staff", "Sales Staff"].includes(updatedRole)) {
      console.error("The signed-in user's Firestore profile has an invalid role.");
      toast("Your account role is invalid. Ask an administrator to check your profile.");
      return;
    }
    if (updatedRole !== currentUser.role) {
      toast("Your access role changed. Updating your workspace…");
      stopFirestoreSubscriptions();
      connectFirebaseUser(user).catch(error => {
        console.error("Could not reload the workspace after a role change.", error);
        setFirebaseLoginError(error.message);
      });
    }
  }, error => {
    console.error("Live role updates failed.", error);
    toast(`Live role updates stopped: ${error.message}`);
  });
  firebaseUnsubscribers.push(profileUnsubscribe);
}

function usersPage() {
  const rows = state.users.map(user => {
    const isStaff = user.role === "Staff" || user.role === "Inventory Clerk";
    const isSalesStaff = user.role === "Sales Staff";
    const roleLabel = user.role === "Project Administrator"
      ? "Project Admin"
      : user.role === "Administrator" ? "Admin" : isSalesStaff ? "Sales Staff" : "Staff";
    const roleClass = isStaff || isSalesStaff ? "low" : "ok";
    const action = user.id === currentUser.id
      ? "<span class='muted'>Current user</span>"
      : `<div class="actions">${canEdit() && (isStaff || isSalesStaff)
        ? `<button class="secondary" data-assign-role="${esc(user.id)}" data-role="${isStaff?"Sales Staff":"Staff"}">Make ${isStaff?"Sales Staff":"Staff"}</button>`
        : ""}${canManageAdmins() && (isStaff || isSalesStaff)
          ? `<button class="primary" data-assign-role="${esc(user.id)}" data-role="Admin">Make Admin</button>`
          : ""}${canEdit() && !isStaff && !isSalesStaff?"<span class='muted'>—</span>":""}</div>`;
    const workspace = isStaff ? "Time Clock" : isSalesStaff ? "Point of Sale + Time Clock" : "Management";
    return `<tr><td><div class="user-row"><div class="avatar">${esc(user.name?.[0] || "?")}</div><b>${esc(user.name || "Unnamed user")}</b></div></td><td>${esc(user.email || "")}</td><td><span class="pill ${roleClass}">${esc(roleLabel)}</span></td><td>${workspace}</td><td>${action}</td></tr>`;
  }).join("");
  return `<div class="page"><div class="panel"><div class="section-head"><div><h3>System users</h3><p class="panel-sub">Review accounts and workspace roles.</p></div></div><div class="table-wrap"><table><thead><tr><th>User</th><th>Email</th><th>Role</th><th>Workspace access</th><th>Actions</th></tr></thead><tbody>${rows || `<tr><td colspan="5"><div class="empty">No user profiles found.</div></td></tr>`}</tbody></table></div><p class="hint">New registrations are Staff with Time Clock access. Sales Staff can use both Point of Sale and Time Clock. Admins can change Staff and Sales Staff roles. Only a Project Admin can assign Admin access.</p></div></div>`;
}

async function assignUserRole(userId, role) {
  if (!canEdit() || (role === "Admin" && !canManageAdmins())) {
    toast(role === "Admin" ? "Only a Project Admin can assign Admin access." : "Only an administrator can change staff roles.");
    return;
  }
  const user = state.users.find(item => item.id === userId);
  if (!user || !["Inventory Clerk", "Staff", "Sales Staff"].includes(user.role)
      || !["Staff", "Sales Staff", "Admin"].includes(role)) {
    toast("This role change is not allowed.");
    return;
  }
  if (role === "Admin" && !confirm(`Change ${user.name || user.email}'s role to Admin?`)) return;

  try {
    await firebaseDb.collection("users").doc(userId).update({
      role
    });
    toast(`Role changed to ${role}. The user's workspace will update automatically.`);
  } catch (error) {
    console.error("Could not change user role.", error);
    toast(`Role update failed: ${error.message}`);
  }
}

const originalReportsPage = reportsPage;
reportsPage = function() {
  return originalReportsPage().replace(
    "Reports reflect the records currently stored in this browser. For multi-device use, connect Firebase as described in README.",
    "Reports show the latest Firestore data shared by signed-in devices."
  );
};

function init() {
  if (!firebaseConfigIsComplete(window.XAVERY_FIREBASE_CONFIG)) {
    setFirebaseLoginError("Firebase configuration is missing. Check firebase-config.js.");
    return;
  }

  try {
    const app = firebase.initializeApp(window.XAVERY_FIREBASE_CONFIG);
    firebaseAuth = app.auth();
    firebaseDb = app.firestore();
    firebaseRtdb = app.database();
    firebaseStorage = app.storage();
  } catch (error) {
    console.error("Firebase initialization failed.", error);
    setFirebaseLoginError(`Firebase could not start: ${error.message}`);
    return;
  }

  $("#loginForm").addEventListener("submit", async event => {
    event.preventDefault();
    setFirebaseLoginError("");
    authActionInProgress = true;
    let signedInUser = null;
    try {
      const credential = await firebaseAuth.signInWithEmailAndPassword(
        $("#loginEmail").value.trim(),
        $("#loginPassword").value
      );
      await credential.user.reload();
      if (!credential.user.emailVerified) {
        let verificationMessage = "";
        try {
          await credential.user.sendEmailVerification();
          verificationMessage = " A new verification link has been sent.";
        } catch (verificationError) {
          console.error("Verification email could not be sent.", verificationError);
          verificationMessage = ` We couldn't send a verification link: ${verificationError.message}`;
        }
        await firebaseAuth.signOut();
        setFirebaseLoginError(
          `Verify your email before signing in.${verificationMessage}`,
          !verificationMessage.includes("couldn't")
        );
        return;
      }
      signedInUser = credential.user;
    } catch (error) {
      console.error("Firebase sign-in failed.", error);
      setFirebaseLoginError(error.message);
    } finally {
      authActionInProgress = false;
    }
    if (signedInUser) {
      authActionInProgress = true;
      try {
        await connectFirebaseUser(signedInUser);
      } catch (error) {
        console.error("Unable to load the Firebase user profile or inventory.", error);
        setFirebaseLoginError(error.message);
        try {
          await firebaseAuth.signOut();
        } catch (signOutError) {
          console.error("Could not sign out after workspace connection failed.", signOutError);
        }
      } finally {
        authActionInProgress = false;
      }
    }
  });
  $("#forgotPasswordBtn").onclick = async () => {
    const emailInput = $("#loginEmail");
    if (!emailInput.value.trim() || !emailInput.checkValidity()) {
      setFirebaseLoginError("Enter a valid email address to reset your password.");
      emailInput.focus();
      return;
    }
    setFirebaseLoginError("");
    try {
      await firebaseAuth.sendPasswordResetEmail(emailInput.value.trim());
      setFirebaseLoginError("Password reset email sent. Check your inbox.", true);
    } catch (error) {
      console.error("Password reset email could not be sent.", error);
      setFirebaseLoginError(error.message);
    }
  };
  $("#registerBtn").onclick = () => {
    setFirebaseLoginError("");
    openRegistrationModal();
  };
  $("#logoutBtn").onclick = async () => {
    stopCamera();
    stopFirestoreSubscriptions();
    try {
      await firebaseAuth.signOut();
      clearLoginCredentials();
    } catch (error) {
      console.error("Firebase sign-out failed.", error);
      toast(`Sign out failed: ${error.message}`);
    }
  };
  $$(".nav").forEach(button => button.onclick = () => navigate(button.dataset.page));
  $("#topAddBtn").onclick = () => openProductModal();

  firebaseAuth.onAuthStateChanged(async user => {
    if (registrationInProgress || authActionInProgress) return;
    if (!user) {
      stopFirestoreSubscriptions();
      currentUser = null;
      clearLoginCredentials();
      $("#app").classList.add("hidden");
      $("#loginScreen").classList.remove("hidden");
      return;
    }
    try {
      await user.reload();
    } catch (error) {
      console.error("Could not refresh Firebase email verification status.", error);
      setFirebaseLoginError(`Could not check email verification: ${error.message}`);
      await firebaseAuth.signOut();
      return;
    }
    if (!user.emailVerified) {
      setFirebaseLoginError("Verify your email before signing in. Enter your credentials and select Sign in to send a new verification link.");
      await firebaseAuth.signOut();
      return;
    }
    setFirebaseLoginError("Connecting securely to Firestore…");
    try {
      await connectFirebaseUser(user);
    } catch (error) {
      console.error("Unable to load the Firebase user profile or inventory.", error);
      setFirebaseLoginError(error.message);
      try {
        await firebaseAuth.signOut();
      } catch (signOutError) {
        console.error("Could not sign out an account without a valid profile.", signOutError);
      }
    }
  });
}

init();
let cart = [];
let checkoutInProgress = false;

function posPage() {
  const productOptions = state.products
    .filter(p => p.quantity > 0)
    .map(p => `<option value="${esc(p.id)}">${esc(p.name)} (Stock: ${p.quantity}) - ${money(p.price)}</option>`)
    .join('');

  const cartRows = cart.length === 0
    ? `<tr><td colspan="5" class="empty">Cart is empty. Select items to add.</td></tr>`
    : cart.map((item, index) => `
        <tr>
          <td><b>${esc(item.name)}</b></td>
          <td>${money(item.price)}</td>
          <td><input class="cart-quantity" type="number" min="1" max="${item.maxStock}" value="${item.qty}" data-cart-index="${index}"></td>
          <td><b>${money(item.price * item.qty)}</b></td>
          <td><button class="danger small" data-remove-cart="${index}">✕</button></td>
        </tr>
      `).join('');

  const subtotal = cart.reduce((sum, item) => sum + (item.price * item.qty), 0);

  return `
    <div class="page">
      <div class="pos-layout">
        <div class="panel">
          <h3>POS Checkout</h3>
          <p class="panel-sub">Select parts or scan them to add items to this sale.</p>
          <div class="pos-product-controls">
            <select id="posProductSelect">
              <option value="">-- Select Product --</option>
              ${productOptions}
            </select>
            <input type="number" id="posQtyInput" value="1" min="1" step="1" placeholder="Qty">
            <button class="primary" id="addToCartBtn">Add to Cart</button>
          </div>
          <div class="table-wrap"><table>
            <thead><tr><th>Item</th><th>Price</th><th>Qty</th><th>Total</th><th>Action</th></tr></thead>
            <tbody>${cartRows}</tbody>
          </table></div>
        </div>
        <div class="panel">
          <h3>Order Summary</h3>
          <div class="order-summary"><span>Items count</span><b>${cart.reduce((sum, item) => sum + Number(item.qty), 0)}</b></div>
          <div class="order-total"><span>Total amount</span><b>${money(subtotal)}</b></div>
          <button class="primary full" id="checkoutBtn" ${cart.length === 0 ? 'disabled' : ''}>Complete Sale</button>
        </div>
      </div>
      ${scannerPanel(true)}
    </div>
  `;
}
function addToCart() {
  const prodId = $("#posProductSelect").value;
  const qty = Number($("#posQtyInput").value);
  if (!prodId) return toast("Please select a product.");
  addProductToCart(prodId, qty);
}
function addProductToCart(productId, quantity, refresh=true) {
  const product=state.products.find(item=>item.id===productId);
  if(!product){toast("That product is no longer available.");return false}
  if(!Number.isInteger(quantity)||quantity<1){toast("Enter a valid whole-number quantity.");return false}
  const existing = cart.find(i => i.id === product.id);
  const currentInCart=existing?existing.qty:0;
  if(currentInCart+quantity>product.quantity){toast(`Cannot add more than available stock (${product.quantity}).`);return false}
  if(existing){
    existing.qty+=quantity;
    existing.price=Number(product.price)||0;
    existing.maxStock=product.quantity;
  } else {
    cart.push({id:product.id,name:product.name,price:Number(product.price)||0,qty:quantity,maxStock:product.quantity});
  }
  toast(`Added ${product.name} to cart.`);
  if(refresh)renderPage();
  return true;
}

function updateCartQty(index, newQty) {
  const item=cart[index],qty=Number(newQty);
  if(!item)return;
  if(!Number.isInteger(qty)||qty<1){toast("Enter a valid whole-number quantity.");renderPage();return}
  if(qty>item.maxStock){toast(`Max available stock is ${item.maxStock}`);renderPage();return}
  item.qty=qty;
  renderPage();
}

function removeFromCart(index) {
  cart.splice(index, 1);
  renderPage();
}

async function processCheckout() {
  if (cart.length === 0||checkoutInProgress) return;
  checkoutInProgress=true;
  try{
    const productsBefore=structuredClone(state.products),movementsBefore=structuredClone(state.movements);
    const saleId=nextId("S");
    if(cart.some(item=>!state.products.some(product=>product.id===item.id&&item.qty<=product.quantity))){
      toast("One or more cart items no longer have enough stock. Refresh the cart and try again.");
      return;
    }
    for(const item of cart){
      const product=state.products.find(current=>current.id===item.id);
      item.price=Number(product.price)||0;
      product.quantity-=item.qty;
      recordMovement(product,"OUT",item.qty,`POS Sale (${currentUser.name})`,item.price*item.qty,saleId);
    }
    if(!await saveDB()){
      state.products=productsBefore;
      state.movements=movementsBefore;
      renderPage();
      return;
    }
    cart = [];
    toast("Sale completed successfully!");
    renderPage();
  }finally{
    checkoutInProgress=false;
  }
}