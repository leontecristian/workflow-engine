import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ConsoleSession } from './console-session';
import { normalizeViewerReadError } from '@workflow-engine/ui';
import type { WorkflowViewerReads, WorkflowViewerNavigation, ViewerDestination, InstanceView, HistoryPage, HistoryEvent, InstanceGraph } from '@workflow-engine/ui';
@Injectable({providedIn:'root'})
export class ConsoleViewerReads implements WorkflowViewerReads {
  private readonly session = inject(ConsoleSession);
  private async read<T>(path:string, signal:AbortSignal):Promise<T> {
    try { return await this.session.viewerRead<T>(path, signal); }
    catch(error) { throw normalizeViewerReadError(error); }
  }
  instance(id:string,signal:AbortSignal) { return this.read<InstanceView>('/instances/'+encodeURIComponent(id),signal); }
  history(id:string,afterSequence:string,limit:number,signal:AbortSignal) { return this.read<HistoryPage>('/instances/'+encodeURIComponent(id)+'/history?'+new URLSearchParams({afterSequence,limit:String(limit)}),signal); }
  historyEvent(id:string,sequence:string,signal:AbortSignal) { return this.read<HistoryEvent>('/instances/'+encodeURIComponent(id)+'/history/'+encodeURIComponent(sequence),signal); }
  graph(id:string,signal:AbortSignal) { return this.read<InstanceGraph>('/instances/'+encodeURIComponent(id)+'/graph',signal); }
}
@Injectable({providedIn:'root'})
export class ConsoleViewerNavigation implements WorkflowViewerNavigation {
  private readonly router=inject(Router);
  destination(intent:ViewerDestination) {
    switch(intent.kind) {
      case 'commands': return this.router.createUrlTree(['/commands']);
      case 'instanceTasks': return this.router.createUrlTree(['/instance-tasks',intent.id]);
      case 'submissions': return this.router.createUrlTree(['/submissions',intent.id]);
      case 'instance': return this.router.createUrlTree(['/instance',intent.instanceId]);
      case 'history': return this.router.createUrlTree(['/history',intent.instanceId]);
      case 'event': return this.router.createUrlTree(['/history-event',intent.instanceId,intent.sequence]);
      case 'task': return this.router.createUrlTree(['/task',intent.id]);
      case 'activity': return this.router.createUrlTree(['/activity',intent.id]);
      case 'definition': return this.router.createUrlTree(['/definition',intent.id]);
      case 'version': return this.router.createUrlTree(['/version',intent.id]);
    }
  }
}
