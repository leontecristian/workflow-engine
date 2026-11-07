export type Decimal = string;
export type JsonText = string;
export interface AdministrationPage<T> { items:T[]; nextAfterId:string|null; }
export interface DefinitionView {
  id: Decimal;
  key: string;
  name: string;
  description: string | null;
  createdAt: string;
  createdBy: string;
  lockVersion: Decimal;
}
export interface VersionSummary {
  id: Decimal;
  definitionId: Decimal;
  key: string;
  version: number;
  schemaVersion: number;
  status: "DRAFT" | "PUBLISHED" | "RETIRED";
  contentRevision: Decimal;
  lockVersion: Decimal;
  contentHash: string;
  canonicalizationVersion: string;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
  publishedAt: string | null;
  publishedBy: string | null;
  retiredAt: string | null;
  retiredBy: string | null;
  validation: unknown;
}
export interface VersionDetails {
  summary: VersionSummary;
  documentJson: JsonText;
  validationDetailsJson: JsonText | null;
}
export interface Change<T> {
  before: T | null;
  after: T;
  changed: boolean;
}
export interface DefinitionChange extends Change<VersionSummary> {
  operation: string;
  context: unknown;
}
export interface Diagnostic {
  severity: string;
  code: string;
  message: string;
  stage: string;
  pointer: string | null;
}
export interface ValidationReport {
  valid: boolean;
  diagnostics: Diagnostic[];
  detailsOmitted: boolean;
}
export interface DefinitionCheck {
  change: DefinitionChange;
  report: ValidationReport;
  published: boolean;
}
export interface TaskTypeView {
  id: Decimal;
  code: string;
  name: string;
  description: string | null;
  selectable: boolean;
  lockVersion: Decimal;
}
export interface TaskTypeInput {
  name: string;
  description: string | null;
  selectable: boolean;
}
