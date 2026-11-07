import { Component, computed, signal } from '@angular/core';
import { findNodeAtLocation, parseTree } from 'jsonc-parser';
import { prettyDocument, type VariablePage } from '@workflow-engine/ui';
import { RuntimeState } from './runtime-state';
import { PAGE_IMPORTS } from './page-imports';
@Component({selector:'workflow-variables',standalone:true,imports:[...PAGE_IMPORTS],styleUrl:'./runtime.scss',templateUrl:'./variables.html'})
export class WorkflowVariablesComponent extends RuntimeState {
  variables = signal<VariablePage|null>(null);
  variableDisplays = computed(() => {
    const page = this.variables();
    if (!page) return [];
    const source = page.exactJson;
    const tree = source ? parseTree(source) : undefined;
    return page.items.map((item, index) => {
      const node = tree && findNodeAtLocation(tree, ["items", index, "value"]);
      let displayValue = "No stored value";
      if (item.initialized) {
        if (item.value === null) displayValue = "null";
        else if (item.type !== "JSON" && typeof item.value === "string") displayValue = item.value;
        else if (node && source) {
          // Slice the original response: JS number conversion would lose precision and scale.
          displayValue = prettyDocument(source.slice(node.offset, node.offset + node.length));
        } else if (typeof item.value === "boolean") displayValue = String(item.value);
        else displayValue = "Value unavailable; refresh variables.";
      }
      return { ...item, displayValue };
    });
  });
  variableError = computed(()=>this.error()?this.error()+'. Refresh variables to restart paging.':'');

 protected clearContent(){this.variables.set(null);}
 async load(){await this.loadVariables();}
 async loadVariables(next=false){
  const previous=this.variables();
  await this.run(async(signal,current)=>{
   const page=await this.api.variables(this.id,next?previous?.nextAfterKey||undefined:undefined,next?previous?.lockVersion:undefined,signal);
   if(current())this.variables.set(page);
  });
 }
 exportVariables(){const text=this.variables()?.exactJson;if(text)download(text,'workflow-variables.json');}
}
export function download(text:string,name:string){
 const url=URL.createObjectURL(new Blob([text],{type:'application/json'}));
 const a=document.createElement('a');a.href=url;a.download=name;a.click();URL.revokeObjectURL(url);
}
