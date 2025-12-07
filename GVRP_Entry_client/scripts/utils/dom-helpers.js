/**
 * DOM Helper Utilities
 * Reusable DOM manipulation functions
 */

export class DOMHelpers {
    /**
     * Safely get element by ID
     * @param {string} id - Element ID
     * @returns {HTMLElement|null}
     */
    static getElement(id) {
        const element = document.getElementById(id);
        if (!element) {
            console.warn(`Element with id "${id}" not found`);
        }
        return element;
    }

    /**
     * Safely get element value
     * @param {string} id - Element ID
     * @returns {string}
     */
    static getValue(id) {
        const element = this.getElement(id);
        return element?.value || '';
    }

    /**
     * Set element value
     * @param {string} id - Element ID
     * @param {string} value - Value to set
     */
    static setValue(id, value) {
        const element = this.getElement(id);
        if (element) {
            element.value = value;
        }
    }

    /**
     * Get form data as object
     * @param {string} formId - Form element ID
     * @returns {Object}
     */
    static getFormData(formId) {
        const form = this.getElement(formId);
        if (!form) return {};

        const formData = new FormData(form);
        const data = {};

        for (const [key, value] of formData.entries()) {
            data[key] = value;
        }

        return data;
    }

    /**
     * Clear form inputs
     * @param {string} formId - Form element ID
     */
    static resetForm(formId) {
        const form = this.getElement(formId);
        if (form) {
            form.reset();
        }
    }

    /**
     * Show/Hide element
     * @param {string} id - Element ID
     * @param {boolean} show - Whether to show or hide
     */
    static toggle(id, show) {
        const element = this.getElement(id);
        if (element) {
            element.style.display = show ? 'block' : 'none';
        }
    }

    /**
     * Add class to element
     * @param {string} id - Element ID
     * @param {string} className - Class name to add
     */
    static addClass(id, className) {
        const element = this.getElement(id);
        if (element) {
            element.classList.add(className);
        }
    }

    /**
     * Remove class from element
     * @param {string} id - Element ID
     * @param {string} className - Class name to remove
     */
    static removeClass(id, className) {
        const element = this.getElement(id);
        if (element) {
            element.classList.remove(className);
        }
    }

    /**
     * Toggle class on element
     * @param {string} id - Element ID
     * @param {string} className - Class name to toggle
     */
    static toggleClass(id, className) {
        const element = this.getElement(id);
        if (element) {
            element.classList.toggle(className);
        }
    }

    /**
     * Set text content
     * @param {string} id - Element ID
     * @param {string} text - Text content
     */
    static setText(id, text) {
        const element = this.getElement(id);
        if (element) {
            element.textContent = text;
        }
    }

    /**
     * Set HTML content
     * @param {string} id - Element ID
     * @param {string} html - HTML content
     */
    static setHTML(id, html) {
        const element = this.getElement(id);
        if (element) {
            element.innerHTML = html;
        }
    }

    /**
     * Create element with attributes
     * @param {string} tag - HTML tag name
     * @param {Object} attributes - Element attributes
     * @param {string} content - Inner content (text or HTML)
     * @returns {HTMLElement}
     */
    static createElement(tag, attributes = {}, content = '') {
        const element = document.createElement(tag);

        // Set attributes
        Object.entries(attributes).forEach(([key, value]) => {
            if (key === 'class') {
                element.className = value;
            } else if (key === 'dataset') {
                Object.entries(value).forEach(([dataKey, dataValue]) => {
                    element.dataset[dataKey] = dataValue;
                });
            } else {
                element.setAttribute(key, value);
            }
        });

        // Set content
        if (content) {
            if (attributes.html) {
                element.innerHTML = content;
            } else {
                element.textContent = content;
            }
        }

        return element;
    }

    /**
     * Remove all child elements
     * @param {string} id - Parent element ID
     */
    static clearChildren(id) {
        const element = this.getElement(id);
        if (element) {
            element.innerHTML = '';
        }
    }

    /**
     * Append child element
     * @param {string} parentId - Parent element ID
     * @param {HTMLElement} child - Child element to append
     */
    static appendChild(parentId, child) {
        const parent = this.getElement(parentId);
        if (parent && child) {
            parent.appendChild(child);
        }
    }

    /**
     * Remove element
     * @param {string} id - Element ID
     */
    static removeElement(id) {
        const element = this.getElement(id);
        if (element) {
            element.remove();
        }
    }

    /**
     * Scroll element into view
     * @param {string} id - Element ID
     * @param {Object} options - ScrollIntoView options
     */
    static scrollIntoView(id, options = { behavior: 'smooth', block: 'center' }) {
        const element = this.getElement(id);
        if (element) {
            element.scrollIntoView(options);
        }
    }

    /**
     * Disable/Enable form element
     * @param {string} id - Element ID
     * @param {boolean} disabled - Whether to disable
     */
    static setDisabled(id, disabled) {
        const element = this.getElement(id);
        if (element) {
            element.disabled = disabled;
        }
    }

    /**
     * Check if element exists in DOM
     * @param {string} id - Element ID
     * @returns {boolean}
     */
    static exists(id) {
        return !!document.getElementById(id);
    }

    /**
     * Query selector helper
     * @param {string} selector - CSS selector
     * @param {HTMLElement} parent - Parent element (default: document)
     * @returns {HTMLElement|null}
     */
    static $(selector, parent = document) {
        return parent.querySelector(selector);
    }

    /**
     * Query selector all helper
     * @param {string} selector - CSS selector
     * @param {HTMLElement} parent - Parent element (default: document)
     * @returns {NodeList}
     */
    static $$(selector, parent = document) {
        return parent.querySelectorAll(selector);
    }
}

// Export shortcuts
export const {
    getElement,
    getValue,
    setValue,
    getFormData,
    resetForm,
    toggle,
    addClass,
    removeClass,
    toggleClass,
    setText,
    setHTML,
    createElement,
    clearChildren,
    appendChild,
    removeElement,
    scrollIntoView,
    setDisabled,
    exists,
    $,
    $$
} = DOMHelpers;