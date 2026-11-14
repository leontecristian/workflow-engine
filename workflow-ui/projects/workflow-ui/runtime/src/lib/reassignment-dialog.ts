import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { NgModel } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WORKFLOW_TASK_REASSIGNMENT, freezeEnvelope, type TaskView, type WorkflowReassignmentTarget } from '@workflow-engine/ui';
@Component({selector:'workflow-reassignment-dialog',standalone:true,
 imports:[FormsModule,MatDialogModule,MatButtonModule,MatFormFieldModule,MatInputModule,MatSelectModule,MatProgressSpinnerModule],
 template:`<h2 mat-dialog-title>Reassign task #{{data.task.id}}</h2><mat-dialog-content>
 <p>Current assignment: {{data.task.assignment.kind}}: {{data.task.assignment.ref}}<br>Assignee: {{data.task.assigneeRef || 'Unassigned'}}</p>
 <p>Existing ownership will be replaced. Review inputs and obligations remain unchanged.</p>
 <div class="reassignment-fields">
 <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Target kind</mat-label><mat-select [(ngModel)]="kind" (selectionChange)="reset()">@for(k of kinds;track k){<mat-option [value]="k">{{k}}</mat-option>}</mat-select></mat-form-field>
 <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Search destinations</mat-label><input matInput [(ngModel)]="query" maxlength="200" (ngModelChange)="resetChoices()"></mat-form-field>
 <div class="destination-search-actions">
  <button mat-button (click)="search()" [disabled]="busy">Search</button>
  @if(busy){<mat-spinner diameter="16" aria-hidden="true"/>}
  <span class="search-status" role="status">@if(busy){Searching destinations…}@else if(searched){ {{items.length}} {{items.length === 1 ? 'destination' : 'destinations'}} loaded@if(next){ · More available}}</span>
 </div>
 <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Destination</mat-label><mat-select [(ngModel)]="ref">@for(target of items;track target.ref){<mat-option [value]="target.ref">{{target.label}} ({{target.ref}})</mat-option>}</mat-select></mat-form-field>
 @if(next){<button mat-button (click)="search(true)" [disabled]="busy">More destinations</button>}
 <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Reason</mat-label><textarea matInput #reasonControl="ngModel" [(ngModel)]="reason" maxlength="1000" rows="4" required></textarea>@if(reasonControl.hasError('required')){<mat-error>A reason is required.</mat-error>}</mat-form-field>
 @if(error){<p role="alert">{{error}}</p>}
 </div></mat-dialog-content><mat-dialog-actions align="end"><button mat-button mat-dialog-close>Keep assignment</button><button mat-flat-button (click)="confirm(reasonControl)" [disabled]="busy || !ref">Reassign task</button></mat-dialog-actions>`,
 styles:['.reassignment-fields{display:grid;gap:16px;min-width:0}.reassignment-fields>button{justify-self:start}.destination-search-actions{display:flex;align-items:center;flex-wrap:wrap;gap:12px}.search-status{font-size:12px;color:var(--mat-sys-on-surface-variant,inherit)}mat-form-field{width:100%;min-width:0}p{overflow-wrap:anywhere}']})
export class WorkflowReassignmentDialog {
 readonly data=inject<{task:TaskView;version:string}>(MAT_DIALOG_DATA);
 private readonly provider=inject(WORKFLOW_TASK_REASSIGNMENT);
 private readonly dialog=inject(MatDialogRef<WorkflowReassignmentDialog>);
 readonly kinds=['USER','GROUP','ROLE'] as const;kind:'USER'|'GROUP'|'ROLE'='USER';query='';ref='';reason='';error='';searched=false;
 private readonly searching=signal(false);
 get busy(){return this.searching();}
 set busy(value:boolean){this.searching.set(value);}
 items:WorkflowReassignmentTarget[]=[];next:string|null=null;private controller?:AbortController;private sequence=0;private destroyed=false;
 constructor(){inject(DestroyRef).onDestroy(()=>{this.destroyed=true;this.resetChoices();});}
 reset(){this.query='';this.resetChoices();}
 resetChoices(){this.sequence++;this.controller?.abort();this.items=[];this.next=null;this.ref='';this.busy=false;this.error='';this.searched=false;}
 async search(more=false){
  if(this.busy||this.destroyed)return;const cursor=more?this.next:undefined;if(more&&!cursor)return;
  const sequence=++this.sequence,controller=this.controller=new AbortController();this.busy=true;this.searched=false;this.error='';this.ref='';
  try{const page=await this.provider.targets(this.data.task.id,this.kind,this.query,cursor??undefined,20,controller.signal);
   if(this.destroyed||sequence!==this.sequence)return;this.items=more?[...this.items,...page.items]:page.items;this.next=page.nextCursor;this.searched=true;
  }catch{if(!this.destroyed&&sequence===this.sequence){this.items=[];this.next=null;this.error='Destinations could not be loaded. Search again.';}}
  finally{if(!this.destroyed&&sequence===this.sequence)this.busy=false;}
 }
 confirm(reasonControl?:NgModel){
  reasonControl?.control.markAsTouched();
  try{if(!this.items.some(item=>item.ref===this.ref))throw Error('Choose a destination from the current search.');
   const envelope=freezeEnvelope({kind:'reassign',body:{commandId:crypto.randomUUID(),instanceId:this.data.task.instanceId,taskId:this.data.task.id,assignmentKind:this.kind,assignmentRef:this.ref,reason:this.reason},expectedToken:this.data.version});
   this.dialog.close(envelope);
  }catch(error){this.error=(error as Error).message;}
 }
}
