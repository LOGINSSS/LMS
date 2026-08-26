import gsap from 'gsap'

/**
 * 按钮动效指令（v-btn-fx）
 *
 * 用途：给按钮添加 hover 轻放大、按下回弹的 GSAP 交互动效，
 * 全部只动 transform（性能友好），样式类无需改动。
 *
 * 用法：<button v-btn-fx class="btn">...</button>
 */
export const btnFx = {
  mounted(el) {
    // hover 放大 / 离开还原
    el.addEventListener('mouseenter', () => {
      if (el.disabled) return
      gsap.to(el, { scale: 1.05, duration: 0.25, ease: 'power2.out' })
    })
    el.addEventListener('mouseleave', () => {
      gsap.to(el, { scale: 1, duration: 0.25, ease: 'power2.out' })
    })
    // 按下轻微压缩 / 松开回弹（back.out 带一点过冲）
    el.addEventListener('mousedown', () => {
      if (el.disabled) return
      gsap.to(el, { scale: 0.94, duration: 0.12, ease: 'power1.out' })
    })
    el.addEventListener('mouseup', () => {
      gsap.to(el, { scale: 1.05, duration: 0.2, ease: 'back.out(2)' })
    })
  }
}
