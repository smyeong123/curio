import { afterEach, describe, expect, it, vi } from 'vitest'
import { installGlassLight } from '@/utils/glassLight'

function pointer(target: Element, x: number, y: number, pointerType = 'mouse') {
  const event = new MouseEvent('pointermove', { bubbles: true, clientX: x, clientY: y })
  Object.defineProperty(event, 'pointerType', { value: pointerType })
  target.dispatchEvent(event)
}

function glassButton() {
  const button = document.createElement('button')
  button.className = 'btn-editorial'
  button.innerHTML = '<span>Label</span>'
  button.getBoundingClientRect = () => ({ left: 100, top: 20, width: 200, height: 40 }) as DOMRect
  document.body.appendChild(button)
  return button
}

describe('installGlassLight', () => {
  let uninstall = () => {}
  afterEach(() => {
    uninstall()
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  it('writes the pointer position inside a glass button as --gx/--gy', () => {
    uninstall = installGlassLight()
    const button = glassButton()
    pointer(button.querySelector('span')!, 150, 30)
    expect(button.style.getPropertyValue('--gx')).toBe('25.0%')
    expect(button.style.getPropertyValue('--gy')).toBe('25.0%')
  })

  it('drifts the button toward the pointer and springs it home on leave', () => {
    uninstall = installGlassLight()
    const button = glassButton()
    pointer(button, 300, 60) // bottom-right corner
    expect(button.style.getPropertyValue('--lx')).toBe('4.00px')
    expect(button.style.getPropertyValue('--ly')).toBe('3.00px')
    const out = new MouseEvent('pointerout', { bubbles: true, relatedTarget: document.body })
    button.dispatchEvent(out)
    expect(button.style.getPropertyValue('--lx')).toBe('')
    expect(button.style.getPropertyValue('--ly')).toBe('')
  })

  it('ignores touch pointers and non-glass elements', () => {
    uninstall = installGlassLight()
    const button = glassButton()
    pointer(button, 150, 30, 'touch')
    expect(button.style.getPropertyValue('--gx')).toBe('')
    const plain = document.createElement('div')
    document.body.appendChild(plain)
    expect(() => pointer(plain, 10, 10)).not.toThrow()
  })

  it('does nothing under reduced motion', () => {
    vi.spyOn(window, 'matchMedia').mockReturnValue({ matches: true } as MediaQueryList)
    uninstall = installGlassLight()
    const button = glassButton()
    pointer(button, 150, 30)
    expect(button.style.getPropertyValue('--gx')).toBe('')
  })
})
