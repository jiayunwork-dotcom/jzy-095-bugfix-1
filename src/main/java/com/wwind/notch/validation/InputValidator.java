package com.wwind.notch.validation;

import com.wwind.notch.model.LoadingSequenceRequest;
import com.wwind.notch.model.MaterialParameters;
import com.wwind.notch.model.NotchAssessmentRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Central input validation. Kept separate from the numerical kernel: nothing here
 * performs any stress/strain calculation.
 *
 * Rules enforced:
 *  - E, K, n must be present, finite and strictly positive (non-positive -> error);
 *  - Kt must be finite and >= 1;
 *  - nominal stress(es) must be finite; the batch list must be non-empty.
 */
@Component
public class InputValidator {

    public void validate(NotchAssessmentRequest request) {
        List<String> reasons = new ArrayList<>();
        if (request == null) {
            throw new ValidationException(List.of("请求体不能为空"));
        }
        validateMaterial(request.material(), reasons);
        validateKt(request.kt(), reasons);
        validateFinite("nominalStress", request.nominalStress(), reasons);
        if (!reasons.isEmpty()) {
            throw new ValidationException(reasons);
        }
    }

    public void validate(LoadingSequenceRequest request) {
        List<String> reasons = new ArrayList<>();
        if (request == null) {
            throw new ValidationException(List.of("请求体不能为空"));
        }
        validateMaterial(request.material(), reasons);
        validateKt(request.kt(), reasons);
        if (request.nominalStresses() == null) {
            reasons.add("nominalStresses 名义应力序列不能为空");
        } else if (request.nominalStresses().isEmpty()) {
            reasons.add("nominalStresses 名义应力序列至少包含一个点");
        } else {
            for (int i = 0; i < request.nominalStresses().size(); i++) {
                Double s = request.nominalStresses().get(i);
                if (s == null) {
                    reasons.add("nominalStresses[" + i + "] 不能为空");
                } else if (!Double.isFinite(s)) {
                    reasons.add("nominalStresses[" + i + "] 必须为有限数值");
                }
            }
        }
        if (!reasons.isEmpty()) {
            throw new ValidationException(reasons);
        }
    }

    private void validateMaterial(MaterialParameters material, List<String> reasons) {
        if (material == null) {
            reasons.add("material 材料参数对象缺失，需要 elasticModulus/strengthCoefficient/hardeningExponent");
            return;
        }
        validatePositive("material.elasticModulus 弹性模量 E", material.elasticModulus(), reasons);
        validatePositive("material.strengthCoefficient 强度系数 K", material.strengthCoefficient(), reasons);
        validatePositive("material.hardeningExponent 硬化指数 n", material.hardeningExponent(), reasons);
    }

    private void validatePositive(String label, double value, List<String> reasons) {
        if (Double.isNaN(value)) {
            reasons.add(label + " 不能为 NaN");
        } else if (Double.isInfinite(value)) {
            reasons.add(label + " 必须为有限数值");
        } else if (value <= 0.0) {
            reasons.add(label + " 必须为正数，当前值为 " + value);
        }
    }

    private void validateFinite(String label, double value, List<String> reasons) {
        if (!Double.isFinite(value)) {
            reasons.add(label + " 必须为有限数值");
        }
    }

    private void validateKt(double kt, List<String> reasons) {
        if (Double.isNaN(kt) || Double.isInfinite(kt)) {
            reasons.add("kt 应力集中系数必须为有限数值");
        } else if (kt < 1.0) {
            reasons.add("kt 应力集中系数不能小于 1（无缺口即 Kt=1），当前值为 " + kt);
        }
    }
}
