package cn.course.shopping.common;

import com.fasterxml.jackson.databind.JsonNode;

/** 明确拒绝 JSON 类型强制转换，例如 true、1.0 和 "1" 均不能作为编号。 */
public final class Input {
    private Input() {}
    public static JsonNode object(JsonNode data) {
        if (data == null || !data.isObject()) throw new ApiException(400, "JSON 须为对象");
        return data;
    }
    public static String text(JsonNode data, String key, int min, int max) {
        return text(data, key, min, max, true);
    }
    public static String text(JsonNode data, String key, int min, int max, boolean strip) {
        object(data);
        JsonNode node = data.get(key);
        if (node != null && !node.isTextual()) throw new ApiException(400, key + " 必须是文本");
        String value = node == null ? "" : node.textValue();
        if (strip) value = value.strip();
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max) throw new ApiException(400, key + " 长度应为 " + min + "–" + max + " 个字符");
        return value;
    }
    public static long id(JsonNode data, String key) {
        object(data);
        JsonNode node = data.get(key);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() <= 0)
            throw new ApiException(400, key + " 必须是正整数");
        return node.longValue();
    }
}
