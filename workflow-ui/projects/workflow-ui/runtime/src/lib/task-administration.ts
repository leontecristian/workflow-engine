import { Component, signal } from '@angular/core';
import type { MatSelect } from '@angular/material/select';
import type { TaskAdministrationFilters, TaskAdministrationRow } from '@workflow-engine/ui';
import { RuntimeState } from './runtime-state';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { PAGE_IMPORTS } from './page-imports';
let nextFilterPanelId=0;
@Component({selector:'workflow-task-administration',standalone:true,imports:[...PAGE_IMPORTS,MatTableModule,MatChipsModule],styleUrls:['./runtime.scss','./task-administration.scss'],templateUrl:'./task-administration.html'})
export class WorkflowTaskAdministrationComponent extends RuntimeState {
 readonly meta={title:'Task administration',eyebrow:'ADMINISTRATION',description:'Inspect tasks across authorized instances and manage open assignments.',icon:'assignment_turned_in'};
 filters:TaskAdministrationFilters={status:'OPEN'};
 filtersExpanded=false;
 readonly filterPanelId=`workflow-task-filters-${nextFilterPanelId++}`;
 readonly filterLabels={taskName:'Task name',taskType:'Task type code',assignmentKind:'Assignment kind',assignmentRef:'Assignment reference',assigneeRef:'Assignee reference',instanceId:'Instance ID'} as const;
 appliedFilters=signal<TaskAdministrationFilters|null>(null);
 appliedChips(){const filters=this.appliedFilters();return filters?Object.entries(this.filterLabels).flatMap(([key,label])=>{const value=filters[key as keyof typeof this.filterLabels];return value?[{key:key as keyof typeof this.filterLabels,label,value}]:[];}):[];}
 rows=signal<TaskAdministrationRow[]>([]);next:string|null=null;pageSize=50;pageIndex=0;
 private pageCursors:(string|undefined)[]=[undefined];
 protected clearContent(){this.rows.set([]);this.next=null;this.pageCursors=[undefined];this.pageIndex=0;this.appliedFilters.set(null);}
 filtersChanged(){this.invalidate();this.clearContent();this.error.set('');}
 async load(){
  this.clearContent();
  await this.loadPage(undefined,[undefined],0);
 }
 async removeFilter(key:keyof typeof this.filterLabels){
  const applied=this.appliedFilters();if(this.busy()||!applied)return;
  this.filters={...applied};delete this.filters[key];
  if(key==='assignmentKind')delete this.filters.assignmentRef;
  await this.load();
 }
 private async loadPage(cursor:string|undefined,cursors:(string|undefined)[],index:number,size=this.pageSize){
  this.rows.set([]);this.next=null;this.appliedFilters.set(null);
  await this.run(async(signal,current)=>{
   if(!this.api.administrationTasks)throw Error('Task administration is unavailable.');
   if(this.filters.assignmentRef&&!this.filters.assignmentKind)throw Error('Choose an assignment kind to filter by reference.');
   const filters=Object.fromEntries(Object.entries(this.filters).filter(([,value])=>value!=='')) as unknown as TaskAdministrationFilters;
   const page=await this.api.administrationTasks(filters,cursor,size,signal);
   if(current()){this.rows.set(page.items);this.next=page.nextCursor;this.pageCursors=cursors;this.pageIndex=index;this.pageSize=size;this.appliedFilters.set(filters);}
  });
 }
 async nextPage(){
  if(this.busy()||!this.next)return;
  await this.loadPage(this.next,[...this.pageCursors.slice(0,this.pageIndex+1),this.next],this.pageIndex+1);
 }
 async previousPage(){
  if(this.busy()||this.pageIndex===0)return;
  await this.loadPage(this.pageCursors[this.pageIndex-1],this.pageCursors,this.pageIndex-1);
 }
 async changePageSize(size:number,select?:MatSelect){
  if(this.busy()||![20,50,100].includes(size))return;
  this.clearContent();
  await this.loadPage(undefined,[undefined],0,size);
  if(select)select.value=this.pageSize;
 }
}
