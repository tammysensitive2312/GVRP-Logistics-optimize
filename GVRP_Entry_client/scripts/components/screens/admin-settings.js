export class AdminSettings {
    static #currentSection = 'vehicle-types';
    static #sidebarElement = null;

    static init() {
        this.#sidebarElement = document.getElementById('admin-sidebar');

        // Initialize search
        this.#initSearch();
    }

    static #initSearch() {
        const searchVTypes = document.getElementById('search-vehicle-types');
        if (searchVTypes) {
            searchVTypes.addEventListener('input', (e) => {
                VehicleTypeManager.search(e.target.value);
            });
        }

        const searchVehicles = document.getElementById('search-vehicles');
        if (searchVehicles) {
            searchVehicles.addEventListener('input', (e) => {
                VehicleManager.search(e.target.value);
            });
        }
    }

    static switchSection(sectionName) {
        this.#currentSection = sectionName;

        // Update sidebar active state
        document.querySelectorAll('.admin-menu-item').forEach(item => {
            item.classList.remove('active');
        });
        document.querySelector(`[data-section="${sectionName}"]`)?.classList.add('active');

        // Update content sections
        document.querySelectorAll('.admin-section').forEach(section => {
            section.classList.remove('active');
        });
        document.getElementById(`${sectionName}-section`)?.classList.add('active');

        // Load data for section
        switch (sectionName) {
            case 'vehicle-types':
                VehicleTypeManager.load();
                break;
            case 'vehicles':
                VehicleManager.load();
                break;
            case 'depots':
                // Future implementation
                break;
        }
    }

    static toggleSidebar() {
        if (this.#sidebarElement) {
            this.#sidebarElement.classList.toggle('show');
        }
    }

    static async loadCounts() {
        try {
            const [vehicleTypes, vehicles, depots] = await Promise.all([
                getVehicleTypes(),
                getVehicle(0, 1000),
                getDepots()
            ]);

            document.getElementById('vehicle-types-count').textContent = vehicleTypes?.length || 0;
            document.getElementById('vehicles-count').textContent = vehicles?.content?.length || 0;
            document.getElementById('depots-count').textContent = depots?.length || 0;
        } catch (error) {
            console.error('Failed to load counts:', error);
        }
    }
}

if (typeof window !== 'undefined') {
    window.AdminSettings = AdminSettings;
}
