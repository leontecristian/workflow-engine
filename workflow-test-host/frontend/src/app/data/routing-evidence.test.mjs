import test from "node:test";
import assert from "node:assert/strict";
import { routingEvidence } from "./routing-evidence.ts";

test("routing display retains persisted order, exact values and skipped reads", () => {
  const value={nodeKey:"ROUTE",selectedTransitionKey:"high",usedDefault:false,evaluations:[
    {transitionKey:"first",result:false,reads:[]},
    {transitionKey:"high",result:true,reads:[{variableKey:"quantity",type:"LONG",value:"9007199254740993"}]},
  ]};
  assert.deepEqual(routingEvidence("GATEWAY_ROUTED",JSON.stringify(value)),value);
  assert.equal(routingEvidence("VARIABLE_WRITTEN",JSON.stringify(value)),null);
  assert.equal(routingEvidence("VARIABLES_WRITTEN",JSON.stringify(value)),null);
});
test("malformed or lossy routing display falls back to raw history", () => {
  assert.equal(routingEvidence("GATEWAY_ROUTED","{"),null);
  assert.equal(routingEvidence("GATEWAY_ROUTED",JSON.stringify({nodeKey:"R",selectedTransitionKey:"t",usedDefault:false,evaluations:[
    {transitionKey:"t",result:true,reads:[{variableKey:"n",type:"LONG",value:9007199254740992}]},
  ]})),null);
});
