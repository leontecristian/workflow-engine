import type { CommandResult, ReplayResult } from '../models';
export type CommandBody = Readonly<Record<string,string|null>>;
export type Envelope = Readonly<{kind:'start'|'complete';body:CommandBody;expectedToken:string|null}>;
/** The only host-specific envelope: start fields and dispatch remain host-owned. */
export interface StartEnvelopeAdapter {
 freezeBody(value:unknown):CommandBody;
 execute(envelope:Envelope,signal:AbortSignal):Promise<CommandResult>;
 replay(envelope:Envelope,signal:AbortSignal):Promise<ReplayResult>;
}
export function exactDecimal(value:string,zero=false):string {
 if(typeof value!=='string'||!/^(0|[1-9][0-9]*)$/.test(value)||(!zero&&value==='0')||BigInt(value)>9223372036854775807n) throw new Error('Invalid decimal identifier');
 return value;
}
export function freezeEnvelope(input:unknown,start?:StartEnvelopeAdapter):Envelope {
 if(!input||typeof input!=='object'||Array.isArray(input))throw new Error('Invalid command envelope');
 const obj=input as Record<string,unknown>;
 if(Object.keys(obj).sort().join()!=='body,expectedToken,kind'||(obj['kind']!=='start'&&obj['kind']!=='complete'))throw new Error('Invalid command envelope');
 if(!obj['body']||typeof obj['body']!=='object'||Array.isArray(obj['body']))throw new Error('Invalid body');
 const source=obj['body'] as Record<string,unknown>;
 if(typeof source['commandId']!=='string'||!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(source['commandId']))throw new Error('Invalid command UUID');
 let body:CommandBody;
 if(obj['kind']==='start') {
  if(!start)throw new Error('Host start adapter is required');
  if(obj['expectedToken']!==null)throw new Error('Unexpected token');
  body=start.freezeBody(source);
  if(body['commandId']!==source['commandId'])throw new Error('Start adapter changed command UUID');
 } else {
  if(Object.keys(source).sort().join()!=='commandId,comment,instanceId,outcomeKey,taskId')throw new Error('Invalid command fields');
  for(const [key,value] of Object.entries(source))if(typeof value!=='string'&&!(key==='comment'&&value===null))throw new Error('Invalid command value');
  exactDecimal(source['instanceId'] as string);exactDecimal(source['taskId'] as string);
  exactDecimal(obj['expectedToken'] as string,true);
  body={...source} as CommandBody;
 }
 return Object.freeze({kind:obj['kind'],body:Object.freeze({...body}),expectedToken:obj['expectedToken'] as string|null});
}
export class CommandBuffer {
 actor:string|null=null;
 command:Envelope|null=null;
 entries:Envelope[]=[];
 constructor(private readonly start?:StartEnvelopeAdapter){}
 setActor(actor:string|null){if(this.actor!==actor)this.clear();this.actor=actor;}
 remember(value:unknown):Envelope {
  const frozen=freezeEnvelope(value,this.start);
  const previous=this.entries.find(item=>item.body['commandId']===frozen.body['commandId']);
  if(previous&&(previous.kind!==frozen.kind||previous.expectedToken!==frozen.expectedToken||commandIdentity(previous.body)!==commandIdentity(frozen.body)))throw new Error('This UUID already has a different original request in the command buffer');
  if(!previous)this.entries.push(frozen);
  this.command=previous||frozen;return this.command;
 }
 select(id:string){this.command=this.entries.find(item=>item.body['commandId']===id)||null;}
 clear(){this.command=null;this.entries=[];}
 restore(text:string){return this.remember(parseExactJson(text));}
 export(){return JSON.stringify(this.command,null,2);}
}
/** Reject duplicates before JSON.parse can discard them. Numeric values are never reserialized as inputs. */
export function parseExactJson(text:string):unknown {
 const tokens=text.match(/"(?:\\.|[^"\\])*"|[{}\[\],:]|true|false|null|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g)||[];
 const stack:(Set<string>|null)[]=[];
 for(let i=0;i<tokens.length;i++) {
  const t=tokens[i];
  if(t==='{')stack.push(new Set());else if(t==='[')stack.push(null);else if(t==='}'||t===']')stack.pop();
  else if(t.startsWith('"')&&tokens[i+1]===':') {const key=JSON.parse(t),current=stack[stack.length-1];if(current?.has(key))throw new Error('Duplicate JSON property');current?.add(key);}
 }
 return JSON.parse(text);
}
function commandIdentity(body:CommandBody){return JSON.stringify(Object.fromEntries(Object.entries(body).sort(([a],[b])=>a.localeCompare(b))));}
