import gsap from 'gsap'
import { onUnmounted } from 'vue'

/**
 * 卡片批量入场动画（stagger）
 *
 * 用途：列表数据加载完成后，容器子元素依次淡入上移；
 * 翻页/筛选后调用 play() 重播。只动 transform/opacity。
 *
 * 用法：
 *   const grid = ref(null)
 *   const { play } = useEntrance(grid)
 *   await load()          // 数据到位、DOM 渲染后
 *   play()
 */
export function useEntrance(containerRef) {
  let ctx = null

  const play = () => {
    if (!containerRef.value || containerRef.value.children.length === 0) return
    // 重播前先还原上一次动画的起点状态，保证每次都从隐藏状态入场
    if (ctx) ctx.revert()
    ctx = gsap.context(() => {
      gsap.fromTo(
        containerRef.value.children,
        { y: 24, opacity: 0 },
        { y: 0, opacity: 1, stagger: 0.07, duration: 0.5, ease: 'power2.out' }
      )
    }, containerRef.value)
  }

  // 组件卸载时统一还原，杜绝幽灵动画与内存泄漏
  onUnmounted(() => {
    if (ctx) ctx.revert()
  })

  return { play }
}
