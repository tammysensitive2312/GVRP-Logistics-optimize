/**
 * Form Validation Module
 * Centralized validation logic for all forms
 */

export class Validator {
    /**
     * Validate depot form data
     * @param {Object} data - Depot form data
     * @returns {Object} { isValid: boolean, errors: string[] }
     */
    static validateDepot(data) {
        const errors = [];

        if (!data.name || data.name.trim() === '') {
            errors.push('Vui lòng nhập tên depot');
        }

        if (!data.latitude || !data.longitude) {
            errors.push('Vui lòng chọn vị trí trên bản đồ');
        }

        if (data.latitude && (data.latitude < -90 || data.latitude > 90)) {
            errors.push('Latitude phải nằm trong khoảng -90 đến 90');
        }

        if (data.longitude && (data.longitude < -180 || data.longitude > 180)) {
            errors.push('Longitude phải nằm trong khoảng -180 đến 180');
        }

        return {
            isValid: errors.length === 0,
            errors
        };
    }

    /**
     * Validate fleet form data
     * @param {Object} data - Fleet form data
     * @returns {Object} { isValid: boolean, errors: string[] }
     */
    static validateFleet(data) {
        const errors = [];

        if (!data.fleet_name || data.fleet_name.trim() === '') {
            errors.push('Vui lòng nhập tên đội xe');
        }

        if (!data.vehicles || data.vehicles.length === 0) {
            errors.push('Vui lòng thêm ít nhất 1 xe');
        }

        // Validate each vehicle
        data.vehicles?.forEach((vehicle, index) => {
            const vehicleErrors = this.validateVehicle(vehicle, index + 1);
            errors.push(...vehicleErrors);
        });

        return {
            isValid: errors.length === 0,
            errors
        };
    }

    /**
     * Validate single vehicle data
     * @param {Object} vehicle - Vehicle data
     * @param {number} index - Vehicle number for error messages
     * @returns {string[]} Array of error messages
     */
    static validateVehicle(vehicle, index) {
        const errors = [];
        const prefix = `Xe #${index}:`;

        if (!vehicle.vehicle_license_plate || vehicle.vehicle_license_plate.trim() === '') {
            errors.push(`${prefix} Vui lòng nhập biển số`);
        }

        if (!vehicle.capacity || vehicle.capacity <= 0) {
            errors.push(`${prefix} Tải trọng phải lớn hơn 0`);
        }

        if (!vehicle.start_depot_id) {
            errors.push(`${prefix} Vui lòng chọn điểm xuất phát`);
        }

        if (!vehicle.end_depot_id) {
            errors.push(`${prefix} Vui lòng chọn điểm kết thúc`);
        }

        if (vehicle.max_distance && vehicle.max_distance < 0) {
            errors.push(`${prefix} Quãng đường tối đa không được âm`);
        }

        if (vehicle.max_duration && vehicle.max_duration < 0) {
            errors.push(`${prefix} Thời gian tối đa không được âm`);
        }

        return errors;
    }

    /**
     * Validate import form data
     * @param {Object} data - Import form data
     * @returns {Object} { isValid: boolean, errors: string[] }
     */
    static validateImport(data) {
        const errors = [];

        if (!data.deliveryDate) {
            errors.push('Vui lòng chọn ngày giao hàng');
        }

        if (data.serviceTime === undefined || data.serviceTime < 0) {
            errors.push('Thời gian phục vụ phải >= 0');
        }

        if (data.method === 'file' && !data.file) {
            errors.push('Vui lòng chọn file');
        }

        if (data.method === 'text' && (!data.textData || data.textData.trim() === '')) {
            errors.push('Vui lòng nhập dữ liệu');
        }

        return {
            isValid: errors.length === 0,
            errors
        };
    }

    /**
     * Validate file upload
     * @param {File} file - File object
     * @param {Object} options - Validation options
     * @returns {Object} { isValid: boolean, error: string }
     */
    static validateFile(file, options = {}) {
        const {
            maxSize = 10 * 1024 * 1024, // 10MB
            allowedExtensions = ['.csv', '.json']
        } = options;

        if (!file) {
            return { isValid: false, error: 'Không có file được chọn' };
        }

        // Check file size
        if (file.size > maxSize) {
            const maxSizeMB = (maxSize / (1024 * 1024)).toFixed(0);
            return {
                isValid: false,
                error: `File không được vượt quá ${maxSizeMB}MB`
            };
        }

        // Check file extension
        const fileName = file.name.toLowerCase();
        const isValidExtension = allowedExtensions.some(ext => fileName.endsWith(ext));

        if (!isValidExtension) {
            return {
                isValid: false,
                error: `Chỉ chấp nhận file ${allowedExtensions.join(', ')}`
            };
        }

        return { isValid: true, error: null };
    }

    /**
     * Validate coordinates
     * @param {number} lat - Latitude
     * @param {number} lng - Longitude
     * @returns {boolean}
     */
    static validateCoordinates(lat, lng) {
        return (
            typeof lat === 'number' &&
            typeof lng === 'number' &&
            lat >= -90 && lat <= 90 &&
            lng >= -180 && lng <= 180
        );
    }

    /**
     * Validate email format
     * @param {string} email
     * @returns {boolean}
     */
    static validateEmail(email) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(email);
    }

    /**
     * Validate phone number (Vietnamese format)
     * @param {string} phone
     * @returns {boolean}
     */
    static validatePhone(phone) {
        const phoneRegex = /^(0|\+84)[0-9]{9}$/;
        return phoneRegex.test(phone?.replace(/\s/g, ''));
    }
}

// Export individual validation functions for convenience
export const {
    validateDepot,
    validateFleet,
    validateVehicle,
    validateImport,
    validateFile,
    validateCoordinates,
    validateEmail,
    validatePhone
} = Validator;