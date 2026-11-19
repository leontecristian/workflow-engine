package io.github.leontecristian.workflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.service.definition.TaskResultSchema;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowResultForm;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import org.junit.jupiter.api.Test;

class WorkflowResultFormUnitTest {
    private final RuntimeWire wire = new RuntimeWire();

    @Test void projectsNamedFieldsInPinnedOrderAndKeepsExactNumericBounds() {
        var form = form("""
                {"type":"object","additionalProperties":false,"required":["approvedAmount","note"],
                 "properties":{
                   "approvedAmount":{"type":"number","minimum":0,"maximum":9007199254740993.0123456789},
                   "note":{"type":"string","maxLength":500,"title":"Note","description":"For the next reviewer",
                           "examples":[""]},
                   "allowRemote":{"type":"boolean"},
                   "nullableReference":{"type":["string","null"],"format":"uuid"}}}
                """);
        assertThat(form.fields()).extracting(WorkflowResultForm::label)
                .containsExactly("Approved amount", "Note", "Allow remote", "Nullable reference");
        assertThat(form.fields().get(0).limits()).containsEntry("minimum", "0")
                .containsEntry("maximum", "9007199254740993.0123456789");
        assertThat(form.fields().get(1).required()).isTrue();
        assertThat(form.fields().get(1).help()).isEqualTo("For the next reviewer");
        assertThat(form.fields().get(1).hint()).isEqualTo("Empty text");
        assertThat(form.fields().get(1).fixed()).isNull();
        assertThat(form.fields().get(2).required()).isFalse();
        assertThat(form.fields().get(3).nullable()).isTrue();
        assertThat(form.fields().get(3).format()).isEqualTo("uuid");
    }

    @Test void resolvesEscapedReferencesAndPreservesConditionalRulesPerItem() {
        var form = form("""
                {"type":"object","additionalProperties":false,"properties":{
                   "reviews":{"type":"array","minItems":1,"maxItems":5,"items":{"$ref":"#/$defs/item"}}},
                 "$defs":{
                   "item":{"type":"object","additionalProperties":false,
                     "properties":{"decision":{"enum":["APPROVE","REJECT"]},
                                   "reason":{"$ref":"#/$defs/text~1reason%20here","maxLength":500}},
                     "required":["decision"],
                     "if":{"properties":{"decision":{"const":"REJECT"}},"required":["decision"]},
                     "then":{"properties":{"reason":{"minLength":1,"title":"Review reason",
                              "description":"Explain the rejection","examples":["Unavailable"]}},"required":["reason"]}},
                   "text/reason here":{"type":"string","maxLength":1000}}}
                """);
        var list = form.fields().get(0);
        assertThat(list.kind()).isEqualTo("array");
        assertThat(list.limits()).containsEntry("minItems", "1").containsEntry("maxItems", "5");
        var item = list.item();
        assertThat(item.fields().get(1).kind()).isEqualTo("string");
        assertThat(item.fields().get(1).limits()).containsEntry("maxLength", "500");
        var dependency = item.dependencies().get(0);
        assertThat(dependency.when().requiredFields()).containsExactly("decision");
        assertThat(dependency.when().fields().get(0).rule().matches()).isEqualTo("\"REJECT\"");
        assertThat(dependency.then().requiredFields()).containsExactly("reason");
        var reason = dependency.then().fields().get(0).rule();
        assertThat(reason.limits()).containsEntry("minLength", "1");
        assertThat(reason.label()).isEqualTo("Review reason");
        assertThat(reason.help()).isEqualTo("Explain the rejection");
        assertThat(reason.hint()).isEqualTo("Unavailable");
        assertThat(wire.json(form)).doesNotContain("$defs", "$ref", "additionalProperties", "text/reason here");
    }

    @Test void alternativesHaveSeparateNamedFieldsAndExactFixedSelectorValues() {
        var form = form("""
                {"oneOf":[
                  {"type":"object","title":"Booking review","additionalProperties":false,
                   "required":["selectionType","bookingReference"],"properties":{
                      "selectionType":{"type":"string","const":"BOOKING"},
                      "bookingReference":{"type":"string"}}},
                  {"type":"object","additionalProperties":false,
                   "required":["selectionType","resourceNumber"],"properties":{
                      "selectionType":{"type":"string","const":"RESOURCE"},
                      "resourceNumber":{"type":"integer","enum":[9007199254740993,9007199254740996]}}}]}
                """);
        assertThat(form.selectorKey()).isEqualTo("selectionType");
        assertThat(form.selectorLabel()).isEqualTo("Selection type");
        assertThat(form.alternatives()).extracting(WorkflowResultForm.Alternative::label)
                .containsExactly("Booking review", "Resource");
        var booking = form.alternatives().get(0).field();
        assertThat(booking.fields()).extracting(WorkflowResultForm::key)
                .containsExactly("selectionType", "bookingReference");
        assertThat(booking.fields().get(0).fixed()).isEqualTo("\"BOOKING\"");
        var resource = form.alternatives().get(1).field();
        assertThat(resource.fields()).extracting(WorkflowResultForm::key)
                .containsExactly("selectionType", "resourceNumber");
        assertThat(resource.fields().get(1).choices()).extracting(WorkflowResultForm.Choice::value)
                .containsExactly("9007199254740993", "9007199254740996");
        assertThat(wire.json(form)).doesNotContain("oneOf", "const", "enum", "additionalProperties");
    }

    @Test void separatesNullableChoicesAndKeepsOnlyObjectsAsRootChoices() {
        var result = form("""
                {"type":["object","null"],"additionalProperties":false,"properties":{},"enum":[null,{},false]}
                """);
        assertThat(result.nullable()).isFalse();
        assertThat(result.choices()).extracting(WorkflowResultForm.Choice::value).containsExactly("{}");
        var answer = form("""
                {"type":"object","additionalProperties":false,"properties":{"answer":{"enum":[false,null]}}}
                """).fields().get(0);
        assertThat(answer.nullable()).isTrue();
        assertThat(answer.choices()).extracting(WorkflowResultForm.Choice::value).containsExactly("false");
    }

    @Test void substantiallyReusedPinnedControlsAndRulesFitTheContractResponse() {
        var schema = (com.fasterxml.jackson.databind.node.ObjectNode) wire.tree("""
                {"type":"object","additionalProperties":false,"properties":{},"$defs":{
                   "block":{"type":"object","additionalProperties":false,"properties":{},
                     "if":{"properties":{"leaf0":{"const":"apply"}},"required":["leaf0"]},
                     "then":{"required":["leaf1"]}},
                   "leaf":{"type":"string"}}}
                """);
        var definitions = (com.fasterxml.jackson.databind.node.ObjectNode) schema.get("$defs");
        var leaves = (com.fasterxml.jackson.databind.node.ObjectNode) definitions.path("block").path("properties");
        var groups = (com.fasterxml.jackson.databind.node.ObjectNode) schema.get("properties");
        String help = "a".repeat(2000);
        ((com.fasterxml.jackson.databind.node.ObjectNode) definitions.get("leaf")).put("description", help);
        for (int index = 0; index < 70; index++) {
            leaves.putObject("leaf" + index).put("$ref", "#/$defs/leaf");
            groups.putObject("group" + index).put("$ref", "#/$defs/block");
        }
        var contract = new TaskResultContract(TaskResultContract.Mode.REQUIRED, wire.json(schema));
        assertThat(TaskResultSchema.validate(contract, WorkflowJsonValue.parse("{}"))).isNull();

        var form = WorkflowResultForm.fromSchema(schema);
        assertThat(form.fields()).hasSize(70);
        assertThat(form.fields().get(69).fields()).hasSize(70);
        assertThat(form.fields().get(69).fields().get(69)).isSameAs(form.fields().get(0).fields().get(69));
        assertThat(form.fields().get(69).dependencies().get(0).then())
                .isSameAs(form.fields().get(0).dependencies().get(0).then());
        String body = wire.largeResponse(form).getBody();
        assertThat(body).hasSizeLessThan(300_000).doesNotContain("$defs", "$ref", "additionalProperties");
        var projectedGroups = wire.tree(body).path("fields");
        var firstLeaf = projectedGroups.get(0).path("fields").get(69);
        var helpText = projectedGroups.get(0).path("fields").get(0).path("help");
        assertThat(helpText.path("text").textValue()).isEqualTo(help);
        assertThat(firstLeaf.path("help").path("textId").asInt()).isEqualTo(helpText.path("textId").asInt());
        assertThat(projectedGroups.get(69).path("fields").get(69).asInt()).isEqualTo(firstLeaf.path("formId").asInt());
        var firstRule = projectedGroups.get(0).path("dependencies").get(0).path("then");
        assertThat(firstRule.path("requiredFields").get(0).textValue()).isEqualTo("leaf1");
        assertThat(projectedGroups.get(69).path("dependencies").get(0).path("then").asInt())
                .isEqualTo(firstRule.path("ruleId").asInt());
    }

    @Test void sharesLongFixedValuesAcrossDifferentlyNamedControls() {
        var schema = (com.fasterxml.jackson.databind.node.ObjectNode) wire.tree("""
                {"type":"object","additionalProperties":false,"properties":{},"$defs":{"value":{"type":"string"}}}
                """);
        String value = "a".repeat(16_000);
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("$defs").path("value")).put("const", value);
        var groups = (com.fasterxml.jackson.databind.node.ObjectNode) schema.get("properties");
        for (int group = 0; group < 4; group++) {
            var fields = groups.putObject("group" + group).put("type", "object")
                    .put("additionalProperties", false).putObject("properties");
            for (int field = 0; field < 100; field++)
                fields.putObject("value" + (group * 100 + field)).put("$ref", "#/$defs/value");
        }
        String schemaJson = wire.json(schema);
        assertThat(schemaJson).hasSizeLessThan(32_768);
        var contract = new TaskResultContract(TaskResultContract.Mode.REQUIRED, schemaJson);
        assertThat(TaskResultSchema.validate(contract, WorkflowJsonValue.parse("{}"))).isNull();

        var form = WorkflowResultForm.fromSchema(schema);
        var response = wire.largeResponse(form).getBody();
        assertThat(response).hasSizeLessThan(160_000).doesNotContain("$defs", "$ref", "additionalProperties");
        assertThat(wire.largeResponse(form).getBody()).isEqualTo(response);
        var projectedGroups = wire.tree(response).path("fields");
        var first = projectedGroups.get(0).path("fields").get(0);
        var last = projectedGroups.get(3).path("fields").get(99);
        assertThat(first.path("key").textValue()).isEqualTo("value0");
        assertThat(last.path("key").textValue()).isEqualTo("value399");
        var firstFixed = first.path("fixed");
        var lastFixed = last.path("fixed");
        var exactText = first.path("choices").get(0).path("value");
        assertThat(exactText.path("text").textValue()).isEqualTo(wire.json(value));
        assertThat(firstFixed.path("textId").intValue()).isEqualTo(exactText.path("textId").intValue());
        assertThat(lastFixed.path("textId").intValue()).isEqualTo(firstFixed.path("textId").intValue());
        assertThat(lastFixed.has("text")).isFalse();
    }

    @Test void sharesChoiceAndHelpTextWithConditionalRules() {
        var schema = (com.fasterxml.jackson.databind.node.ObjectNode) wire.tree("""
                {"type":"object","additionalProperties":false,"properties":{
                  "first":{"$ref":"#/$defs/choice"},"second":{"$ref":"#/$defs/choice"}},
                 "$defs":{"choice":{"type":"string"}},"if":{"required":["first"]},
                 "then":{"required":["second"],"properties":{"second":{"$ref":"#/$defs/choice"}}}}
                """);
        var definition = (com.fasterxml.jackson.databind.node.ObjectNode) schema.path("$defs").path("choice");
        String help = "See reviewer instructions. ".repeat(20);
        String answer = "Review choice \"A\". ".repeat(20);
        definition.put("description", help).putArray("enum").add(answer);
        var projected = wire.tree(wire.json(WorkflowResultForm.fromSchema(schema)));
        var first = projected.path("fields").get(0);
        var second = projected.path("fields").get(1);
        var firstChoice = first.path("choices").get(0);
        var secondChoice = second.path("choices").get(0);
        assertThat(first.path("help").path("text").textValue()).isEqualTo(help);
        assertThat(firstChoice.path("label").path("text").textValue()).isEqualTo(answer);
        assertThat(firstChoice.path("value").path("text").textValue()).isEqualTo(wire.json(answer));
        assertThat(second.path("help").path("textId").intValue()).isEqualTo(first.path("help").path("textId").intValue());
        assertThat(secondChoice.path("value").path("textId").intValue()).isEqualTo(firstChoice.path("value").path("textId").intValue());
        var allowed = first.path("rule").path("allowed").get(0);
        assertThat(allowed.path("textId").intValue()).isEqualTo(firstChoice.path("value").path("textId").intValue());
    }

    private WorkflowResultForm form(String source) {
        return WorkflowResultForm.fromSchema(wire.tree(source));
    }
}
