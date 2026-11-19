import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ConsoleSession } from './console-session';
import { normalizeViewerReadError } from '@workflow-engine/ui';
import type { BusinessHistoryEvent, BusinessHistoryPage, WorkflowViewerReads, WorkflowViewerNavigation, ViewerDestination, InstanceView, HistoryPage, HistoryEvent, InstanceGraph } from '@workflow-engine/ui';
@Injectable({providedIn:'root'})
export class ConsoleViewerReads implements WorkflowViewerReads {
  private readonly session = inject(ConsoleSession);
  private async read<T>(path:string, signal:AbortSignal):Promise<T> {
    try { return await this.session.viewerRead<T>(path, signal); }
    catch(error) { throw normalizeViewerReadError(error); }
  }
  instance(id:string,signal:AbortSignal) { return this.read<InstanceView>('/instances/'+encodeURIComponent(id),signal); }
  history(id:string,cursor:string,limit:number,signal:AbortSignal) {
    const path = '/instances/' + encodeURIComponent(id);
    if (this.session.admin) {
      const query = new URLSearchParams({afterSequence:cursor,limit:String(limit)});
      return this.read<HistoryPage>(path+'/history?'+query,signal);
    }
    const query = new URLSearchParams({limit:String(limit)});
    if (cursor !== '0') query.set('cursor', cursor);
    return this.read<BusinessHistoryPage>(path+'/business-history?'+query,signal);
  }
  historyEvent(id:string,sequence:string,signal:AbortSignal) {
    const path = '/instances/' + encodeURIComponent(id);
    const ref = encodeURIComponent(sequence);
    return this.session.admin && /^[1-9][0-9]*$/.test(sequence)
      ? this.read<HistoryEvent>(path+'/history/'+ref,signal)
      : this.read<BusinessHistoryEvent>(path+'/business-history/'+ref,signal);
  }
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
