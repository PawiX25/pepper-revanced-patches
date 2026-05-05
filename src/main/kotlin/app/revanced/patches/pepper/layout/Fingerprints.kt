package app.revanced.patches.pepper.layout

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The deal-detail RecyclerView ItemDecoration class (`m07` in v8.12.00).
 * Identified through its `e(I)Z` method — a packed-switch on a synthetic mode
 * field with a long list of view-type IDs in the 0x7f0a00xx range. Mode-1
 * (used by the deal-detail screen) returns true for any of ~13 section/header
 * types, including the 3 ad-cell types.
 *
 * No other ItemDecoration in the app has this "13 distinct R.id.* literals in
 * a single (I)Z method" shape, so the literal-count threshold uniquely picks
 * out m07.
 */
internal val itemDecorationEFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    parameters("I")
    returns("Z")
    custom { method, _ ->
        if (method.name != "e") return@custom false
        val impl = method.implementation ?: return@custom false
        val rIdLiterals = impl.instructions
            .filterIsInstance<NarrowLiteralInstruction>()
            .map { it.narrowLiteral }
            .filter { it in 0x7f0a0000..0x7f0a00ff }
            .toSet()
        rIdLiterals.size >= 10
    }
}

/**
 * `RecyclerView.ItemDecoration.getItemOffsets` override on m07.
 *
 * Signature: `a(Landroid/graphics/Rect; Landroid/view/View;
 *               Landroidx/recyclerview/widget/RecyclerView; L<state>;)V`
 *
 * The 4th param is `RecyclerView.State` after R8 minification — short
 * obfuscated class name (e.g. `Lxm9;`).
 *
 * Body fingerprint: calls `Rect.set(IIII)V` (offset assignment) AND invokes
 * the same class's `e(I)Z` plus `d(II)Z` helpers — both invariants of how
 * m07 dispatches between section spacing and divider spacing.
 */
internal val itemDecorationGetItemOffsetsFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("V")
    custom { method, _ ->
        if (method.name != "a") return@custom false
        val params = method.parameterTypes
        if (params.size != 4) return@custom false
        if (params[0].toString() != "Landroid/graphics/Rect;") return@custom false
        if (params[1].toString() != "Landroid/view/View;") return@custom false
        if (params[2].toString() != "Landroidx/recyclerview/widget/RecyclerView;") return@custom false
        // 4th param is the obfuscated RecyclerView.State alias.
        if (!params[3].toString().matches(Regex("L[a-z][a-z0-9]{0,3};"))) return@custom false

        val impl = method.implementation ?: return@custom false
        val ownClass = method.definingClass.toString()
        val refs = impl.instructions.mapNotNull { (it as? ReferenceInstruction)?.reference }
        val callsRectSet = refs.any { ref ->
            ref is MethodReference &&
                ref.definingClass == "Landroid/graphics/Rect;" &&
                ref.name == "set" &&
                ref.parameterTypes.size == 4
        }
        // Own-class helper invariants distinguish m07 from sibling
        // ItemDecorations (e.g. t4b, which has e(II)Z and d(View)I).
        val callsOwnE = refs.any { ref ->
            ref is MethodReference &&
                ref.definingClass.toString() == ownClass &&
                ref.name == "e" &&
                ref.parameterTypes.size == 1 &&
                ref.parameterTypes[0].toString() == "I" &&
                ref.returnType == "Z"
        }
        val callsOwnD = refs.any { ref ->
            ref is MethodReference &&
                ref.definingClass.toString() == ownClass &&
                ref.name == "d" &&
                ref.parameterTypes.size == 2 &&
                ref.parameterTypes.all { it.toString() == "I" } &&
                ref.returnType == "Z"
        }
        callsRectSet && callsOwnE && callsOwnD
    }
}

/**
 * `RecyclerView.ItemDecoration.onDraw` override on m07.
 *
 * Signature: `b(Landroid/graphics/Canvas; Landroidx/recyclerview/widget/RecyclerView;
 *               L<state>;)V`
 *
 * Body fingerprint:
 *  - drives a per-item drawing loop (calls `LinearLayoutManager.B(I)View`,
 *    the obfuscated `getChildAt`)
 *  - paints via `Drawable.draw(Canvas)V` (the `shadow_divider` drawable)
 *  - invokes `e(I)Z` AND `d(II)Z` on its OWN class — the section/divider
 *    dispatch helpers, with this exact pair of signatures unique to m07
 *
 * The own-class helper invariant is what excludes the sibling ItemDecoration
 * in the app (`t4b`, with `e(II)Z` + `d(Landroid/view/View;)I` helpers
 * instead) — its onDraw matches the canvas/getChildAt/Drawable.draw shape
 * but uses different helper signatures.
 */
internal val itemDecorationOnDrawFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("V")
    custom { method, _ ->
        if (method.name != "b") return@custom false
        val params = method.parameterTypes
        if (params.size != 3) return@custom false
        if (params[0].toString() != "Landroid/graphics/Canvas;") return@custom false
        if (params[1].toString() != "Landroidx/recyclerview/widget/RecyclerView;") return@custom false
        if (!params[2].toString().matches(Regex("L[a-z][a-z0-9]{0,3};"))) return@custom false

        val impl = method.implementation ?: return@custom false
        val ownClass = method.definingClass.toString()
        val refs = impl.instructions.mapNotNull { (it as? ReferenceInstruction)?.reference }
        val refStrings = refs.mapNotNull { it.toString() }

        val callsDrawableDraw = refStrings.any {
            it.contains("Landroid/graphics/drawable/Drawable;->draw(Landroid/graphics/Canvas;)V")
        }
        val callsGetChildAt = refStrings.any {
            it.endsWith("->B(I)Landroid/view/View;")
        }
        val callsOwnE = refs.any { ref ->
            ref is MethodReference &&
                ref.definingClass.toString() == ownClass &&
                ref.name == "e" &&
                ref.parameterTypes.size == 1 &&
                ref.parameterTypes[0].toString() == "I" &&
                ref.returnType == "Z"
        }
        val callsOwnD = refs.any { ref ->
            ref is MethodReference &&
                ref.definingClass.toString() == ownClass &&
                ref.name == "d" &&
                ref.parameterTypes.size == 2 &&
                ref.parameterTypes.all { it.toString() == "I" } &&
                ref.returnType == "Z"
        }
        callsDrawableDraw && callsGetChildAt && callsOwnE && callsOwnD
    }
}

/**
 * Deal-thread card ViewHolder constructor (`j6c.<init>(View)` in Pepper PL
 * v8.13.00). It binds every view inside `item_deal_thread_card`.
 *
 * This is the narrowest safe hook for runtime layout changes: the card XML is
 * already inflated, every target view is non-null and stored on the holder,
 * and sponsored deal cards reuse the same holder through their wrapper.
 */
internal val dealCardViewHolderConstructorFingerprint = fingerprint {
    returns("V")
    parameters("Landroid/view/View;")
    custom { method, _ ->
        if (method.name != "<init>") return@custom false
        val impl = method.implementation ?: return@custom false
        val literals = impl.instructions
            .filterIsInstance<NarrowLiteralInstruction>()
            .map { it.narrowLiteral }
            .toSet()
        val hasTargetIds = listOf(
            0x7f0a01a6, // deal_thread_button_get_deal
            0x7f0a01a7, // deal_thread_button_get_deal_outlined
            0x7f0a01a8, // deal_thread_button_get_deal_small
            0x7f0a01c7, // deal_thread_text_temperature
            0x7f0a0611, // thread_button_comment_count
            0x7f0a0630, // thread_image
        ).all(literals::contains)
        val callsFindViewById = impl.instructions.any { instruction ->
            instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                (instruction as? ReferenceInstruction)?.reference?.toString() ==
                    "Landroid/view/View;->findViewById(I)Landroid/view/View;"
        }
        hasTargetIds && callsFindViewById
    }
}
