import { InjectionToken } from '@angular/core';
import type { AdministrationPage, DefinitionView, VersionSummary, VersionDetails, DefinitionChange, DefinitionCheck, Change, TaskTypeView, TaskTypeInput } from './models';
/** Host-owned authentication, authorization, CSRF and HTTP transport. No write is retried. */
export interface WorkflowAdministrationApi {
 listTaskTypes(filter:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<AdministrationPage<TaskTypeView>>;
 createTaskType(body:TaskTypeInput & {code:string},signal:AbortSignal):Promise<Change<TaskTypeView>>;
 updateTaskType(id:string,body:TaskTypeInput,token:string,signal:AbortSignal):Promise<Change<TaskTypeView>>;
 listDefinitions(cursor:string|undefined,limit:number,signal:AbortSignal):Promise<AdministrationPage<DefinitionView>>;
 definition(id:string,signal:AbortSignal):Promise<DefinitionView>;
 saveMetadata(id:string,name:string,description:string|null,token:string,signal:AbortSignal):Promise<Change<DefinitionView>>;
 listVersions(id:string,status:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<AdministrationPage<VersionSummary>>;
 version(id:string,signal:AbortSignal):Promise<VersionDetails>;
 lookupVersion(key:string,version:string,signal:AbortSignal):Promise<VersionDetails>;
 importDraft(document:string,signal:AbortSignal):Promise<DefinitionChange>;
 saveDocument(id:string,document:string,token:string,signal:AbortSignal):Promise<DefinitionChange>;
 checkVersion(id:string,action:'validate'|'publish',token:string,signal:AbortSignal):Promise<DefinitionCheck>;
 retireVersion(id:string,token:string,signal:AbortSignal):Promise<DefinitionChange>;
 exportVersion(id:string,signal:AbortSignal):Promise<string>;
}
export const WORKFLOW_ADMINISTRATION_API = new InjectionToken<WorkflowAdministrationApi>('Workflow administration API');
export const DEFAULT_ADMINISTRATION_MESSAGES = {
 catalogTitle:'Task types', definitionsTitle:'Definitions', familyTitle:'Workflow family', versionTitle:'Definition version', refresh:'Reload', export:'Export JSON',
 unauthenticated:'Sign in to administer workflows.', denied:'Administration access denied.', failed:'Request failed.',
 refreshFailed:'The write succeeded, but refreshing the view failed.',
};
export type WorkflowAdministrationMessages = typeof DEFAULT_ADMINISTRATION_MESSAGES;
