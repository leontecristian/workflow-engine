import { exactDecimal, freezeResultJson, parseExactJson } from '@workflow-engine/ui';
import type { Envelope, CommandBody } from '@workflow-engine/ui';
export type { Envelope, CommandBody } from '@workflow-engine/ui';
export { exactDecimal, rawDocument, prettyDocument } from '@workflow-engine/ui';
export type ScalarInputs = string;
/** Sandbox start body contract. Business fields deliberately remain outside workflow-ui. */
export function freezeStartBody(value:unknown):CommandBody {
 if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('Invalid body');
 const body={...(value as Record<string,unknown>)};
 const expected=['commandId','definitionVersionId','expectedRequestVersion','requestId'];
 if(Object.hasOwn(body,'inputs'))expected.push('inputs');
 if(Object.keys(body).sort().join()!==expected.sort().join())throw new Error('Invalid command fields');
 for(const [key,item] of Object.entries(body))if(typeof item!=='string')throw new Error('Invalid command value');
 exactDecimal(body['definitionVersionId'] as string);exactDecimal(body['requestId'] as string);exactDecimal(body['expectedRequestVersion'] as string,true);
 if(Object.hasOwn(body,'inputs'))body['inputs']=freezeInputs(body['inputs']);
 return Object.freeze(body) as CommandBody;
}
export function freezeInputs(value:unknown):ScalarInputs {
 if(typeof value!=='string')throw new Error('Inputs must be raw JSON object text');
 const object=parseExactJson(value);
 if(!object||typeof object!=='object'||Array.isArray(object)||Object.keys(object).length>100)throw new Error('Inputs must be an object');
 for(const key of Object.keys(object))if(!/^[A-Za-z][A-Za-z0-9._-]{0,99}$/.test(key))throw new Error('Invalid input key');
 return value;
}
export function parseScalarInputs(text:string):ScalarInputs{return freezeInputs(text);}
export function commandRequest(envelope:Envelope,replay:boolean):{path:string;body:Readonly<Record<string,unknown>>|string;token:string|null} {
 if(envelope.kind==='start')return {path:replay?'/commands/start/replay':'/instances/start',body:startRequest(envelope.body),token:null};
 const {instanceId,taskId,...body}=envelope.body;
 const path=replay?`/commands/${envelope.kind}/replay`:`/instances/${instanceId}/tasks/${taskId}/${envelope.kind}`;
 const requestBody=replay?envelope.body:body;
 if(envelope.kind==='complete'&&Object.hasOwn(requestBody,'resultJson')) {
  const {resultJson,...fields}=requestBody;
  return {path,body:JSON.stringify(fields).slice(0,-1)+',"result":'+freezeResultJson(resultJson)+'}',token:envelope.expectedToken};
 }
 return {path,body:requestBody,token:envelope.expectedToken};
}
function startRequest(body:CommandBody):string {
 const {inputs,...fields}=body;
 const prefix=JSON.stringify(fields);
 return prefix.slice(0,-1)+',"inputs":'+(inputs===undefined?'{}':freezeInputs(inputs))+'}';
}
