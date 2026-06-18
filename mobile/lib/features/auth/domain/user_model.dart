class UserModel {
  const UserModel({
    required this.id,
    required this.name,
    required this.email,
    this.phone,
  });

  final String id;
  final String name;
  final String email;
  final String? phone;

  factory UserModel.fromJson(Map<String, dynamic> json) => UserModel(
        id: json['id'] as String,
        name: json['name'] as String,
        email: json['email'] as String,
        phone: json['phone'] as String?,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'email': email,
        'phone': phone,
      };
}
