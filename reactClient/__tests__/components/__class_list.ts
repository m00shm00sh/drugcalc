declare global {
    interface HTMLElement {
        classNamesAsList(): string[]
    }
}

HTMLElement.prototype.classNamesAsList = function(this: HTMLElement) {
    return this.className.split(' ')
}