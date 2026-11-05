import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadTs } from '../../../../unit/component-loader.mjs';
const { definitionTopology } = loadTs(new URL('./definition-topology.ts', import.meta.url));
const source = { schemaVersion: 1, nodes: [{key:'S',name:'Start',type:'START'},{key:'G',name:'Route',type:'EXCLUSIVE_GATEWAY'},{key:'E',name:'End',type:'END'}], transitions: [{key:'a',from:'S',to:'G'},{key:'b',from:'G',to:'E'},{key:'loop',from:'G',to:'G'}] };
test('saved topology uses existing bounded layout without runtime state or evaluation', () => {
 const original=JSON.stringify(source); const view=definitionTopology(original);
 assert.equal(view.graph.nodes.length,3); assert.equal(view.layout.edges.length,3);
 assert.ok(view.graph.nodes.every(n=>n.state==='PENDING' && n.taskId===null));
 assert.equal(JSON.stringify(source),original);
});
test('invalid, oversized and ambiguous topology fails locally', () => {
 for(const value of ['{','null',JSON.stringify({...source,schemaVersion:2}),JSON.stringify({...source,nodes:[...source.nodes,source.nodes[0]]}),JSON.stringify({...source,transitions:[{key:'x',from:'S',to:'missing'}]}),JSON.stringify({...source,nodes:Array(201).fill(source.nodes[0])})]) assert.equal(definitionTopology(value),null);
});

test('transition inspection preserves saved conditions and clears when source changes', async (t) => {
 const { component } = await import('../../../../unit/component-loader.mjs');
 const { signal } = await import('@angular/core');
 const { DefinitionGraphComponent } = loadTs(new URL('./definition-graph.ts', import.meta.url));
 const { page } = component(t, DefinitionGraphComponent);
 const condition = "#vars.quantity > 10 and '<script>' != ''";
 const doc = {...source, transitions:[{key:'a',from:'S',to:'G',outcome:'NEXT'},{key:'b',from:'G',to:'E',condition},{key:'fallback',from:'G',to:'E',default:true}]};
 const text = signal(JSON.stringify(doc));
 page.document = text;
 page.selectTransition('b');
 assert.equal(page.selectedTransition().condition, condition);
 assert.equal(page.selectedTransition().from, 'G');
 assert.equal(page.selectedTransition().default, false);
 page.selectTransition('fallback');
 assert.equal(page.selectedTransition().default, true);
 assert.equal(page.selectedTransition().condition, null);
 text.set(JSON.stringify({...doc, version:2}));
 assert.equal(page.selectedTransition(), null);
 page.selectTransition('a');
 assert.equal(page.selectedTransition().outcome, 'NEXT');
 page.selection.set(null);
 assert.equal(page.selectedTransition(), null);
});
