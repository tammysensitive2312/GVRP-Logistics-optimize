/**
 * Resizable Divider Component
 * Allows users to resize map/table sections
 */

export class ResizableDivider {
    static #isDragging = false;
    static #startY = 0;
    static #startMapHeight = 0;
    static #divider = null;
    static #mapSection = null;
    static #mainContent = null;

    /**
     * Initialize resizable divider
     */
    static init() {
        this.#divider = document.getElementById('resize-divider');
        this.#mapSection = document.getElementById('map-section');
        this.#mainContent = document.querySelector('.main-content');

        if (!this.#divider || !this.#mapSection) {
            console.warn('Resizable divider elements not found');
            return;
        }

        this.#attachEventListeners();
    }

    /**
     * Attach event listeners
     * @private
     */
    static #attachEventListeners() {
        // Mouse down on divider
        this.#divider.addEventListener('mousedown', (e) => {
            this.#startDragging(e);
        });

        // Mouse move (global)
        document.addEventListener('mousemove', (e) => {
            this.#onDrag(e);
        });

        // Mouse up (global)
        document.addEventListener('mouseup', () => {
            this.#stopDragging();
        });

        // Touch support for mobile
        this.#divider.addEventListener('touchstart', (e) => {
            this.#startDragging(e.touches[0]);
        }, { passive: true });

        document.addEventListener('touchmove', (e) => {
            if (this.#isDragging) {
                this.#onDrag(e.touches[0]);
            }
        });

        document.addEventListener('touchend', () => {
            this.#stopDragging();
        });
    }

    /**
     * Start dragging
     * @private
     */
    static #startDragging(event) {
        this.#isDragging = true;
        this.#startY = event.clientY;
        this.#startMapHeight = this.#mapSection.offsetHeight;

        this.#divider.classList.add('dragging');
        document.body.style.cursor = 'row-resize';
        document.body.style.userSelect = 'none';

        event.preventDefault();
    }

    /**
     * Handle dragging
     * @private
     */
    static #onDrag(event) {
        if (!this.#isDragging) return;

        const deltaY = event.clientY - this.#startY;
        const newMapHeight = this.#startMapHeight + deltaY;

        // Constraints
        const mainHeight = this.#mainContent.offsetHeight;
        const minHeight = 200;
        const maxHeight = mainHeight - 200; // Reserve at least 200px for table

        if (newMapHeight >= minHeight && newMapHeight <= maxHeight) {
            this.#mapSection.style.height = newMapHeight + 'px';

            // Invalidate map size to prevent rendering issues
            this.#invalidateMapSize();
        }
    }

    /**
     * Stop dragging
     * @private
     */
    static #stopDragging() {
        if (this.#isDragging) {
            this.#isDragging = false;
            this.#divider.classList.remove('dragging');
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
        }
    }

    /**
     * Invalidate map size
     * @private
     */
    static #invalidateMapSize() {
        if (typeof MainMap !== 'undefined') {
            MainMap.invalidateSize();
        } else if (typeof mainMap !== 'undefined' && mainMap) {
            mainMap.invalidateSize();
        }
    }

    /**
     * Set map height programmatically
     * @param {number} height - Height in pixels
     */
    static setMapHeight(height) {
        if (!this.#mapSection) return;

        const mainHeight = this.#mainContent.offsetHeight;
        const minHeight = 200;
        const maxHeight = mainHeight - 200;

        const constrainedHeight = Math.max(minHeight, Math.min(height, maxHeight));
        this.#mapSection.style.height = constrainedHeight + 'px';
        this.#invalidateMapSize();
    }

    /**
     * Reset to default height (60vh)
     */
    static resetHeight() {
        if (!this.#mapSection) return;

        this.#mapSection.style.height = '60vh';
        this.#invalidateMapSize();
    }

    /**
     * Get current map height
     * @returns {number} Height in pixels
     */
    static getMapHeight() {
        return this.#mapSection?.offsetHeight || 0;
    }
}

// Backward compatibility
window.initResizableDivider = () => ResizableDivider.init();