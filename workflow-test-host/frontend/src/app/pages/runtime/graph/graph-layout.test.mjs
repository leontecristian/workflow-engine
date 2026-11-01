import {test} from "node:test";
import assert from "node:assert/strict";
import {loadTs, component} from "../../../../../unit/component-loader.mjs";
const {layoutGraph} = loadTs(new URL("./graph-layout.ts", import.meta.url));
const {InstanceGraphComponent} = loadTs(new URL("./instance-graph.ts", import.meta.url));
const node = (key, type="SERVICE_TASK") => ({key,name:key,type,state:"PENDING",activityId:null,taskId:null});
const edge = (key,from,to) => ({key,from,to,outcome:null,traversed:false});
const model = (nodes,edges) => ({instance:{id:"9007199254740993",status:"ACTIVE",currentNodeKey:"REVIEW"},nodes,edges});
const plain = value => JSON.parse(JSON.stringify(value));

test("five-node service chain has dynamic bounds, forward routes and no clipped nodes",()=>{
  const graph=model([node("DONE","END"),node("AFTER"),node("BEGIN","START"),node("BEFORE"),node("REVIEW","HUMAN_TASK")],
    [edge("a","BEGIN","BEFORE"),edge("b","BEFORE","REVIEW"),edge("c","REVIEW","AFTER"),edge("d","AFTER","DONE")]);
  const result=layoutGraph(graph);
  assert.deepEqual(Array.from(result.nodes,n=>n.node.key),["BEGIN","BEFORE","REVIEW","AFTER","DONE"]);
  assert.ok(result.width>1500); assert.equal(result.edges.length,4);
  for(const item of result.nodes){assert.ok(item.x>=0 && item.x+250<result.width);assert.ok(item.y>=0 && item.y+104<result.height);}
  assert.ok(result.edges.every(e=>e.path.includes(" C ")));
  assert.deepEqual(plain(result),plain(layoutGraph({...graph,nodes:[...graph.nodes].reverse(),edges:[...graph.edges].reverse()})));
});
test("branch/merge, back edge, self-loop and disconnected declared nodes have finite deterministic routes",()=>{
  const graph=model([node("BEGIN","START"),node("BRANCH","EXCLUSIVE_GATEWAY"),node("LEFT","HUMAN_TASK"),node("RIGHT","CUSTOM_NODE"),node("DONE","END"),node("ORPHAN")],
    [edge("start","BEGIN","BRANCH"),edge("left","BRANCH","LEFT"),edge("right","BRANCH","RIGHT"),edge("finishLeft","LEFT","DONE"),edge("finishRight","RIGHT","DONE"),edge("back","LEFT","BRANCH"),edge("self","RIGHT","RIGHT")]);
  const result=layoutGraph(graph), positions=new Map(result.nodes.map(n=>[n.node.key,n]));
  assert.equal(positions.get("LEFT").x,positions.get("RIGHT").x);assert.notEqual(positions.get("LEFT").y,positions.get("RIGHT").y);
  assert.ok(result.edges.find(e=>e.edge.key==="back").path.includes(" V "));
  assert.ok(result.edges.find(e=>e.edge.key==="self").path.includes(" V "));
  assert.equal(result.nodes.length,6);assert.ok(result.nodes.some(n=>n.node.type==="CUSTOM_NODE"));
  for(const route of result.edges){assert.ok(!/NaN|Infinity/.test(route.path));assert.ok(route.labelY>=0);assert.ok(route.labelX>=0 && route.labelX<result.width);}
  assert.deepEqual(plain(result),plain(layoutGraph({...graph,nodes:[...graph.nodes].reverse(),edges:[...graph.edges].reverse()})));
});
test("200-node chains and 500 parallel edges remain bounded and visible",()=>{
  const nodes=Array.from({length:200},(_,i)=>node("N"+String(i).padStart(3,"0"),i===0?"START":i===199?"END":"SERVICE_TASK"));
  const edges=nodes.slice(1).map((n,i)=>edge("edge"+i,nodes[i].key,n.key));
  const chain=layoutGraph(model(nodes,edges));assert.equal(chain.nodes.length,200);assert.ok(chain.width>60000);
  assert.ok(chain.nodes.every(n=>n.x+250<chain.width));
  const parallel=layoutGraph(model([node("A","START"),node("B","END")],Array.from({length:500},(_,i)=>edge("e"+i,"A","B"))));
  assert.equal(parallel.edges.length,500);assert.ok(parallel.edges.every(e=>e.labelY>=0 && e.labelY<parallel.height));
  assert.throws(()=>layoutGraph(model([...nodes,node("TOO_MANY")],edges)));
  assert.throws(()=>layoutGraph(model(nodes,[edge("unknown","N000","MISSING")])));
  assert.throws(()=>layoutGraph(model([node("A","START"),node("A")],[])));
});
test("activity-only links, generic labels, zoom clamps and marker IDs preserve exact identity",t=>{
  const {page}=component(t,InstanceGraphComponent);page.graph=()=>model([node("REVIEW","HUMAN_TASK")],[]);
  const multi={...node("REVIEW","HUMAN_TASK"),activityId:"9007199254740997"};
  assert.deepEqual(Array.from(page.route(multi)),["/activity","9007199254740997"]);
  assert.match(page.accessibleName(multi),/Open activity/);assert.equal(page.nodeType(node("before","SERVICE_TASK")),"SERVICE TASK");
  for(let i=0;i<20;i++)page.changeZoom(-0.25);assert.equal(page.zoom(),0.25);
  for(let i=0;i<20;i++)page.changeZoom(0.25);assert.equal(page.zoom(),2);page.zoom.set(1);assert.equal(page.zoomPercent(),100);
  assert.equal(page.arrow(true),"url(#workflow-9007199254740993-visited)");
});
