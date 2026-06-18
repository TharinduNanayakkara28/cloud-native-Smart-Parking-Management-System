class VehicleModel {
  const VehicleModel({
    required this.id,
    required this.plate,
    required this.make,
    required this.model,
    this.isDefault = false,
  });

  final String id;
  final String plate;
  final String make;
  final String model;
  final bool isDefault;

  String get displayName {
    if (make.isNotEmpty && model.isNotEmpty) return '$make $model';
    if (make.isNotEmpty) return make;
    return plate;
  }

  factory VehicleModel.fromJson(Map<String, dynamic> json) => VehicleModel(
        id: json['id'] as String,
        plate: json['plate'] as String,
        make: json['make'] as String? ?? '',
        model: json['model'] as String? ?? '',
        isDefault: json['isDefault'] as bool? ?? false,
      );
}
