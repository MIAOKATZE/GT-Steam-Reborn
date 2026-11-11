// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.api.compat;

import com.miaokatze.gtsr.common.structure.GTSRRedstoneHatchCheck;

/** 控制器自身持有完整结构校验的红石仓事务，不使用全局控制器缓存。 */
public interface IGTSRRedstoneHatchCheck {

    GTSRRedstoneHatchCheck getGTSRRedstoneHatchCheck();
}
